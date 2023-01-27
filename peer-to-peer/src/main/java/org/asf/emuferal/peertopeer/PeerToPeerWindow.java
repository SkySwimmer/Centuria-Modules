package org.asf.emuferal.peertopeer;

import javax.swing.JFrame;
import java.awt.FlowLayout;
import javax.swing.JPanel;
import java.awt.Dimension;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.ListDataListener;

import org.asf.emuferal.peertopeer.players.P2PPlayer;
import org.asf.nexus.*;

import java.awt.Font;
import java.util.UUID;

import javax.swing.JButton;
import javax.swing.JTextField;
import javax.swing.ListModel;

import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;

public class PeerToPeerWindow {

	private JButton btnNewButton;
	private JFrame frame;
	private JTextField textField;
	private NexusClient connector = null;
	private String room;

	/**
	 * Create the application.
	 */
	public PeerToPeerWindow() {
		initialize();
	}

	/**
	 * Initialize the contents of the frame.
	 */
	private void initialize() {
		frame = new JFrame();
		frame.setBounds(100, 100, 640, 453);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setResizable(false);
		frame.setTitle("EmuFeral - Peer to Peer - Control Panel");
		frame.setLocationRelativeTo(null);
		frame.getContentPane().setLayout(new FlowLayout(FlowLayout.CENTER, 5, 5));

		JPanel panel = new JPanel();
		panel.setPreferredSize(new Dimension(600, 400));
		frame.getContentPane().add(panel);
		panel.setLayout(null);

		JLabel lblNewLabel = new JLabel("EmuFeral - Peer to Peer Configuration Panel");
		lblNewLabel.setFont(new Font("Tahoma", Font.PLAIN, 22));
		lblNewLabel.setHorizontalAlignment(SwingConstants.CENTER);
		lblNewLabel.setBounds(10, 11, 580, 67);
		panel.add(lblNewLabel);

		JLabel lblNewLabel_1 = new JLabel("No P2P connection");
		lblNewLabel_1.setBounds(10, 375, 383, 14);
		panel.add(lblNewLabel_1);

		btnNewButton = new JButton("Connect to room");
		btnNewButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (textField.getText().length() < 3 || textField.getText().length() > 25) {
					JOptionPane.showMessageDialog(frame, "Not a valid room ID", "Error", JOptionPane.ERROR_MESSAGE);
					return;
				}
				btnNewButton.setEnabled(false);
				Thread th = new Thread(() -> {
					connectToRoom(textField.getText());
				}, "Connection");
				th.setDaemon(true);
				th.start();
			}
		});
		btnNewButton.setBounds(404, 371, 186, 23);
		panel.add(btnNewButton);

		textField = new JTextField();
		textField.setHorizontalAlignment(SwingConstants.CENTER);
		textField.setBounds(10, 106, 580, 56);
		textField.setText(UUID.randomUUID().toString());
		panel.add(textField);
		textField.setColumns(10);

		JLabel lblNewLabel_2 = new JLabel("Peer-to-Peer Room");
		lblNewLabel_2.setBounds(10, 89, 580, 14);
		panel.add(lblNewLabel_2);

		JLabel lblNewLabel_3 = new JLabel("Players");
		lblNewLabel_3.setBounds(10, 197, 580, 14);
		panel.add(lblNewLabel_3);

		JScrollPane scrollPane = new JScrollPane();
		scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
		scrollPane.setBounds(10, 215, 580, 108);
		panel.add(scrollPane);

		JList<String> list = new JList<String>();
		scrollPane.setViewportView(list);
		PeerToPeerModule.listUpdateEvents.add(() -> {
			P2PPlayer[] players = PeerToPeerModule.getPlayers();
			SwingUtilities.invokeLater(() -> {
				list.setModel(new ListModel<String>() {

					@Override
					public int getSize() {
						return players.length;
					}

					@Override
					public String getElementAt(int index) {
						return players[index].displayName + (players[index].isLocal ? " [local player]" : "");
					}

					@Override
					public void addListDataListener(ListDataListener l) {
					}

					@Override
					public void removeListDataListener(ListDataListener l) {
					}

				});
			});
		});

		btnNewButton.setEnabled(false);
		Thread th = new Thread(() -> {
			connectToRoom(textField.getText());
		}, "Connection");
		th.setDaemon(true);
		th.start();
		th = new Thread(() -> {
			while (true) {
				// Sync label
				if (connector == null) {
					SwingUtilities.invokeLater(() -> {
						lblNewLabel_1.setText("No P2P connection");
					});
				} else {
					if (connector.isConnected()) {
						SwingUtilities.invokeLater(() -> {
							lblNewLabel_1.setText("Connected to " + room);
						});
					} else {
						SwingUtilities.invokeLater(() -> {
							lblNewLabel_1.setText("No P2P connection");
						});
					}
				}

				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
				}
			}
		}, "Peer to Peer Connector Thread");
		th.setDaemon(true);
		th.start();
	}

	protected void connectToRoom(String room) {
		if (connector != null) {
			if (connector.isConnected())
				connector.stop();
			connector = null;
			PeerToPeerModule.connector = null;
			PeerToPeerModule.onDisconnect();
		}

		try {
			// Establish new connection
			connector = new NexusClientBuilder().autoReconnect().withChannel("emuferal/v_b_1_5_3+/" + room).build();
			PeerToPeerModule.connector = connector;
			connector.addDisconnectionEventHandler(id -> {
				if (id.equals(connector.getConnectionID()) && !connector.isDisconnecting()) {
					// Connection loss
					PeerToPeerModule.onDisconnect();
				} else
					PeerToPeerModule.onDisconnect(id);
				if (id.equals(connector.getConnectionID()))
					btnNewButton.setEnabled(false);
			});
			connector.addConnectionEventHandler(id -> {
				if (id.equals(connector.getConnectionID())) {
					btnNewButton.setEnabled(true);
					PeerToPeerModule.onConnect();
				}
			});
			connector.addDirectPacketEventHandler(packet -> {
				PeerToPeerModule.handleNexusPacket(packet);
			});
			connector.addPacketEventHandler(packet -> {
				PeerToPeerModule.handleNexusPacket(packet);
			});
			this.room = room;
		} catch (Exception e) {
			JOptionPane.showMessageDialog(frame,
					"Failed to connect, please check your network connection, if the error persists and you are connected contact Quill#4232.",
					"Error", JOptionPane.ERROR_MESSAGE);
		}
	}

	public void start() {
		frame.setVisible(true);
	}
}
