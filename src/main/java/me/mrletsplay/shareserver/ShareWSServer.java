package me.mrletsplay.shareserver;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Comparator;
import java.util.List;

import org.java_websocket.WebSocket;
import org.java_websocket.framing.CloseFrame;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import me.mrletsplay.shareclientcore.connection.RemoteConnection;
import me.mrletsplay.shareclientcore.connection.message.AddressableMessage;
import me.mrletsplay.shareclientcore.connection.message.ClientHelloMessage;
import me.mrletsplay.shareclientcore.connection.message.Message;
import me.mrletsplay.shareclientcore.connection.message.PeerJoinMessage;
import me.mrletsplay.shareclientcore.connection.message.PeerLeaveMessage;
import me.mrletsplay.shareclientcore.connection.message.ServerHelloMessage;

public class ShareWSServer extends WebSocketServer {

	public ShareWSServer() {
		super(new InetSocketAddress("0.0.0.0", 5473));
	}

	@Override
	public void onOpen(WebSocket conn, ClientHandshake handshake) {
		System.out.println("Client connected");
	}

	@Override
	public void onClose(WebSocket conn, int code, String reason, boolean remote) {
		System.out.println("Client disconnected");
		if(conn.getAttachment() != null) {
			SessionUser user = conn.getAttachment();
			var peers = getPeers(user.session());
			if(peers.isEmpty()) {
				ShareServer.deleteSession(reason);
				return;
			}
			peers.forEach(peer -> send(peer, new PeerLeaveMessage(user.siteID())));
		}
	}

	@Override
	public void onMessage(WebSocket conn, String message) {

	}

	@Override
	public void onMessage(WebSocket conn, ByteBuffer bytes) {
		Message m;
		try {
			m = Message.deserialize(bytes);
		}catch(IOException e) {
			conn.close(CloseFrame.POLICY_VALIDATION, "Invalid message");
			return;
		}

		if(conn.getAttachment() == null) {
			// Only valid message is CLIENT_HELLO
			if(m instanceof ClientHelloMessage hello) {
				Session session = ShareServer.getOrCreateSession(hello.sessionID());
				int siteID = session.getNewSiteID();
				conn.setAttachment(new SessionUser(session, hello.username(), siteID));
				send(conn, new ServerHelloMessage(RemoteConnection.PROTOCOL_VERSION, siteID));
				getPeers(session).forEach(peer -> {
					if(peer != conn) {
						SessionUser user = peer.getAttachment();
						send(conn, new PeerJoinMessage(user.username(), user.siteID()));
					}

					send(peer, new PeerJoinMessage(hello.username(), siteID));
				});
			}else {
				conn.close(CloseFrame.POLICY_VALIDATION, "First message must be CLIENT_HELLO");
			}
			return;
		}

		SessionUser user = conn.getAttachment();
		Session session = user.session();
		boolean isHost = getHost(getPeers(session)).equals(conn);
		System.out.println("[" + user.siteID() + "] " + m);
		switch(m.getType()) {
			case CHANGE -> getPeers(session).forEach(peer -> send(peer, m));
			case REQUEST_FULL_SYNC, REQUEST_CHECKSUM -> {
				AddressableMessage msg = (AddressableMessage) m;
				if(msg.siteID() != user.siteID()) {
					conn.close(CloseFrame.POLICY_VALIDATION, "Invalid site id");
					return;
				}

				send(getHost(getPeers(session)), m);
			}
			case FULL_SYNC, CHECKSUM -> {
				if(!isHost) {
					conn.close(CloseFrame.POLICY_VALIDATION, "Only host can send FULL_SYNC or CHECKSUM");
					return;
				}

				AddressableMessage msg = (AddressableMessage) m;
				if(msg.siteID() == AddressableMessage.BROADCAST_SITE_ID) {
					getPeers(session).forEach(peer -> send(peer, m));
				}else {
					WebSocket peer = getPeer(getPeers(session), msg.siteID());
					if(peer != null) send(peer, m);
				}
			}
			default -> {
				conn.close(CloseFrame.POLICY_VALIDATION, "Invalid message received");
			}
		}
	}

	private List<WebSocket> getPeers(Session session) {
		return getConnections().stream()
			.filter(c -> {
				SessionUser user = c.getAttachment();
				if(user == null) return false;
				return user.session().equals(session);
			})
			.toList();
	}

	private WebSocket getHost(List<WebSocket> peers) {
		return peers.stream()
			.min(Comparator.comparing(p -> p.<SessionUser>getAttachment().siteID()))
			.orElse(null);
	}

	private WebSocket getPeer(List<WebSocket> peers, int siteID) {
		return peers.stream()
			.filter(p -> p.<SessionUser>getAttachment().siteID() == siteID)
			.findFirst().orElse(null);
	}

	@Override
	public void onError(WebSocket conn, Exception ex) {

	}

	@Override
	public void onStart() {

	}

	public void send(WebSocket connection, Message message) {
		try {
			ByteArrayOutputStream bOut = new ByteArrayOutputStream();
			DataOutputStream dOut = new DataOutputStream(bOut);

			dOut.writeUTF(message.getType().name());
			message.serialize(dOut);

			connection.send(bOut.toByteArray());
		} catch (IOException e) {
			connection.close(CloseFrame.PROTOCOL_ERROR);
		}
	}

}
