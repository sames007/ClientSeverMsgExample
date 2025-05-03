package org.example.clientsevermsgexample;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.net.URL;
import java.util.ResourceBundle;

public class MainController implements Initializable {

    // === FXML-bound controls for the simple port-check UI ===
    @FXML
    private ComboBox<String> dropdownPort; // dropdown of common ports
    @FXML
    private TextArea resultArea;           // shows connection results
    @FXML
    private TextField urlName;             // host input
    @FXML
    private Button clearBtn;               // clears the above fields

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Populate the port dropdown with some standard service ports
        dropdownPort.getItems().addAll(
                "7",   // ping
                "13",  // daytime
                "21",  // ftp
                "23",  // telnet
                "71",  // finger
                "80",  // http
                "119", // nntp
                "161"  // snmp
        );
    }

    /**
     * Invoked when the user clicks “Check Connection.”
     * Tries to open a short-lived socket to the given host+port.
     */
    @FXML
    void checkConnection(ActionEvent event) {
        String host = urlName.getText();
        int port = Integer.parseInt(dropdownPort.getValue());

        // Try-with-resources auto-closes the socket
        try (Socket sock = new Socket(host, port)) {
            resultArea.appendText(host + " is listening on port " + port + "\n");
        } catch (UnknownHostException e) {
            // Host name could not be resolved
            resultArea.appendText("Unknown host: " + e.getMessage() + "\n");
        } catch (IOException e) {
            // Connection refused or timed out
            resultArea.appendText(host + " not listening on port " + port + "\n");
        }
    }

    /**
     * Clears the host/port UI fields and log area.
     */
    @FXML
    void clearBtn(ActionEvent event) {
        resultArea.clear();
        urlName.clear();
    }


    // ====================
    // === SERVER SIDE ====
    // ====================

    @FXML
    private TextArea serverChatArea;    // chat history display

    @FXML
    private TextField serverMsgText;    // input field for outgoing messages

    @FXML
    private Button serverSendBtn;       // Send button (disabled until connected)

    private DataInputStream serverDis;  // incoming data from client
    private DataOutputStream serverDos; // outgoing data to client
    private Socket clientSocket;        // accepted client connection

    /**
     * Builds and shows the server chat UI, then spawns the server thread.
     */
    @FXML
    void startServer(ActionEvent event) {
        Stage stage = new Stage();

        // Use a BorderPane so we can semantically separate header, center, and footer.
        BorderPane root = new BorderPane();
        root.getStyleClass().add("root-pane");

        // — HEADER —
        Label header = new Label("Server Chat");
        header.getStyleClass().add("header-label");
        HBox headerBox = new HBox(header);
        headerBox.getStyleClass().add("header-box");
        headerBox.setAlignment(Pos.CENTER);
        root.setTop(headerBox);

        // — CENTER: chat history —
        serverChatArea = new TextArea();
        serverChatArea.getStyleClass().add("chat-area");
        serverChatArea.setEditable(false);
        serverChatArea.setWrapText(true);
        serverChatArea.setPrefSize(380, 260);
        VBox centerBox = new VBox(serverChatArea);
        centerBox.setPadding(new Insets(10, 20, 10, 20));
        root.setCenter(centerBox);

        // — FOOTER: input + send —
        serverMsgText = new TextField();
        serverMsgText.getStyleClass().add("message-field");
        serverMsgText.setPromptText("Type a message...");
        HBox.setHgrow(serverMsgText, Priority.ALWAYS);

        serverSendBtn = new Button("Send");
        serverSendBtn.getStyleClass().add("send-button");
        serverSendBtn.setDisable(true);
        serverSendBtn.setOnAction(e -> sendServerMessage());

        HBox inputBox = new HBox(10, serverMsgText, serverSendBtn);
        inputBox.getStyleClass().add("input-box");
        inputBox.setPadding(new Insets(0, 20, 20, 20));
        root.setBottom(inputBox);

        // — SCENE & SHOW —
        Scene scene = new Scene(root, 420, 380);
        scene.getStylesheets().add(getClass().getResource("styles/server.css").toExternalForm());
        stage.setScene(scene);
        stage.setTitle("Server");
        stage.show();

        new Thread(this::runServer).start();
    }

    /**
     * Accepts one client and then loops reading messages.
     * Enables Send button once connected.
     */
    private void runServer() {
        try (ServerSocket serverSocket = new ServerSocket(6666)) {
            appendServerText("Waiting for client...");

            clientSocket = serverSocket.accept();
            appendServerText("Client connected!");

            // Enable Send now that a client is connected
            Platform.runLater(() -> serverSendBtn.setDisable(false));

            // Wrap streams for UTF messaging
            serverDis = new DataInputStream(clientSocket.getInputStream());
            serverDos = new DataOutputStream(clientSocket.getOutputStream());

            // Read until “exit”
            while (true) {
                String msg = serverDis.readUTF();
                appendServerText("Client: " + msg);
                if (msg.equalsIgnoreCase("exit")) break;
            }
        } catch (IOException e) {
            appendServerText("Error: " + e.getMessage());
        } finally {
            cleanupServer();
        }
    }

    /**
     * Sends whatever’s in serverMsgText to the client.
     */
    private void sendServerMessage() {
        String msg = serverMsgText.getText().trim();
        if (msg.isEmpty() || serverDos == null) return;

        try {
            serverDos.writeUTF(msg);
            appendServerText("Me: " + msg);
            serverMsgText.clear();

            if (msg.equalsIgnoreCase("exit")) {
                cleanupServer();
            }
        } catch (IOException ex) {
            appendServerText("Send failed: " + ex.getMessage());
        }
    }

    /**
     * Appends a line to the chat area on the JavaFX thread.
     */
    private void appendServerText(String text) {
        Platform.runLater(() -> serverChatArea.appendText(text + "\n"));
    }

    /**
     * Closes streams & socket, and disables Send button again.
     */
    private void cleanupServer() {
        try {
            if (serverDis != null)    serverDis.close();
            if (serverDos != null)    serverDos.close();
            if (clientSocket != null) clientSocket.close();
        } catch (IOException ignored) {}

        // Disable Send to reflect “no client” state
        Platform.runLater(() -> serverSendBtn.setDisable(true));
    }


    // ====================
    // ==== CLIENT SIDE ===
    // ====================

    // === Class-level fields for client chat functionality ===
    private Button connectBtn;      // “Connect” button, enabled until connected
    private Button clientSendBtn;   // “Send” button, disabled until connected
    private TextField clientMsgText;// Text field where user types messages
    private TextArea clientChatArea;// Read-only area showing chat history
    private DataInputStream clientDis;  // Stream for incoming messages from server
    private DataOutputStream clientDos; // Stream for outgoing messages to server
    private Socket socket1;            // Underlying TCP socket connection

    @FXML
    void startClient(ActionEvent event) {
        Stage stage = new Stage();

        // Root layout
        BorderPane root = new BorderPane();
        root.getStyleClass().add("root-pane");

        // — HEADER —
        Label header = new Label("Client Chat");
        header.getStyleClass().add("header-label");
        HBox headerBox = new HBox(header);
        headerBox.getStyleClass().add("header-box");
        headerBox.setAlignment(Pos.CENTER);
        root.setTop(headerBox);

        // — CENTER: chat history —
        clientChatArea = new TextArea();
        clientChatArea.setEditable(false);
        clientChatArea.setWrapText(true);
        clientChatArea.getStyleClass().add("chat-area");
        VBox centerBox = new VBox(clientChatArea);
        centerBox.setPadding(new Insets(10, 20, 10, 20));
        root.setCenter(centerBox);

        // — FOOTER: input + buttons —
        clientMsgText = new TextField();
        clientMsgText.setPromptText("Type a message...");
        clientMsgText.getStyleClass().add("message-field");
        HBox.setHgrow(clientMsgText, Priority.ALWAYS);

        clientSendBtn = new Button("Send");
        clientSendBtn.setDisable(true);
        clientSendBtn.getStyleClass().add("send-button");
        clientSendBtn.setOnAction(e -> sendClientMessage());

        connectBtn = new Button("Connect");
        connectBtn.getStyleClass().add("send-button");
        connectBtn.setOnAction(this::connectToServer);

        HBox inputBox = new HBox(10, clientMsgText, clientSendBtn, connectBtn);
        inputBox.getStyleClass().add("input-box");
        inputBox.setPadding(new Insets(0, 20, 20, 20));
        root.setBottom(inputBox);

        // Scene & CSS
        Scene scene = new Scene(root, 420, 380);
        scene.getStylesheets().add(getClass().getResource("styles/client.css").toExternalForm());
        stage.setScene(scene);
        stage.setTitle("Client");
        stage.show();
    }

    /**
     * Attempts to connect to the server at localhost:6666.
     * On success: enables the Send button, disables Connect, and starts
     * a background thread to listen for incoming messages.
     */
    private void connectToServer(ActionEvent event) {
        try {
            // 1. Open socket and wrap streams
            socket1 = new Socket("localhost", 6666);
            clientDis = new DataInputStream(socket1.getInputStream());
            clientDos = new DataOutputStream(socket1.getOutputStream());

            // 2. Notify user in the chat area
            appendClientText("Connected to server.");

            // 3. Enable sending, prevent re-connecting
            clientSendBtn.setDisable(false);
            connectBtn.setDisable(true);

            // 4. Launch reader thread to handle incoming messages
            new Thread(() -> {
                try {
                    while (true) {
                        String msg = clientDis.readUTF();       // Block until message arrives
                        appendClientText("Server: " + msg);     // Display it
                        if (msg.equalsIgnoreCase("exit")) break; // Exit on “exit”
                    }
                } catch (IOException e) {
                    // Happens on disconnect or stream error
                    appendClientText("Disconnected.");
                } finally {
                    cleanupClient();  // Ensure resources are closed & Connect is re-enabled
                }
            }).start();

        } catch (IOException e) {
            // Connection failed
            appendClientText("Connection failed: " + e.getMessage());
        }
    }

    /**
     * Sends the user’s current text over the socket, logs it locally,
     * and if the message is “exit” triggers a cleanup.
     */
    private void sendClientMessage() {
        try {
            String msg = clientMsgText.getText();
            clientDos.writeUTF(msg);            // Send to server
            appendClientText("Me: " + msg);     // Log locally
            clientMsgText.clear();              // Clear the input

            // If user typed “exit”, close everything
            if (msg.equalsIgnoreCase("exit")) {
                cleanupClient();
            }
        } catch (IOException e) {
            appendClientText("Send failed: " + e.getMessage());
        }
    }

    /**
     * Safely append a line of text to the chat area on the JavaFX thread.
     */
    private void appendClientText(String text) {
        Platform.runLater(() -> clientChatArea.appendText(text + "\n"));
    }

    /**
     * Closes socket and streams, then re-enables the Connect button
     * so the user can reconnect if desired.
     */
    private void cleanupClient() {
        try {
            if (clientDis != null) clientDis.close();
            if (clientDos != null) clientDos.close();
            if (socket1 != null)   socket1.close();
        } catch (IOException ignored) { }

        // Re-enable Connect on the JavaFX thread
        Platform.runLater(() -> connectBtn.setDisable(false));
    }
}