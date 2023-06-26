import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class ChatServer {
    private ServerSocket serverSocket;
    private List<ClientHandler> clients;
    private static final String LOG_FILE = "server_log.txt";

    private void logMessage(String clientIP, String message) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        String logEntry = "[" + timestamp + "] " + "[" + clientIP + "] " + message;

        try {
            FileWriter fileWriter = new FileWriter(LOG_FILE, true);
            PrintWriter printWriter = new PrintWriter(fileWriter);
            printWriter.println(logEntry);
            printWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("로그 파일에 기록하는 중 오류가 발생했습니다.");
        }
    }
    public static void main(String[] args) {
        ChatServer server = new ChatServer();
        server.start(8080);
    }

    public void start(int port) {
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("Server Operated"); // 서버 구동 메시지

            clients = new ArrayList<>();

            while (true) {
                Socket clientSocket = serverSocket.accept();
                ClientHandler clientHandler = new ClientHandler(clientSocket);
                clientHandler.start();

                clients.add(clientHandler);
                broadcastMessage(clientHandler, "Client " + clientHandler.getClientIP() + " 접속 성공");
                logMessage(clientHandler.getClientIP(), "클라이언트가 서버에 접속했습니다.");
                broadcastClientList();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            close();
        }
    }

    private void broadcastMessage(ClientHandler sender, String message) {
        for (ClientHandler client : clients) {
            client.sendMessage("[" + sender.getClientIP() + "] [" + getCurrentTime() + "] " + message);
        }
    }
    private String getCurrentTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
        return sdf.format(new Date());
    }

    private void broadcastClientList() {
        StringBuilder clientList = new StringBuilder("현재 온라인 클라이언트 목록:\n");
        for (ClientHandler client : clients) {
            clientList.append(client.getClientIP()).append("\n");
        }
        for (ClientHandler client : clients) {
            client.sendMessage(clientList.toString());
        }
    }

    private void close() {
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private class ClientHandler extends Thread {
        private Socket clientSocket;
        private BufferedReader reader;
        private BufferedWriter writer;

        public ClientHandler(Socket socket) {
            clientSocket = socket;
            try {
                reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                writer = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public String getClientIP() {
            return clientSocket.getInetAddress().getHostAddress();
        }

        public void sendMessage(String message) {
            try {
                writer.write(message);
                writer.newLine();
                writer.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void handleCommand(String command) {
            logMessage(getClientIP(), command);
            if (command.startsWith("/chat")) {
                String message = command.substring(6);
                broadcastMessage(this, message);
            } else if (command.startsWith("/help")) {
                String helpMessage = "사용 가능한 명령어:\n" +
                        "/chat [메시지] - 모든 클라이언트에게 메시지 전송\n" +
                        "/help - 명령어 목록과 형식 확인\n" +
                        "/exit - 서버 연결 종료\n" +
                        "/list - 현재 접속 중인 클라이언트 목록 출력\n" +
                        "/dm [IP] [메시지] - 특정 클라이언트에게 메시지 전송\n" +
                        "/file [IP] [파일경로] - 파일 전송";
                sendMessage(helpMessage);
            } else if (command.startsWith("/exit")) {
                sendMessage("서버와의 연결이 종료되었습니다.");
                close();
            } else if (command.startsWith("/list")) {
                broadcastClientList();
            } else if (command.startsWith("/dm")) {
                String[] parts = command.split(" ", 3);
                if (parts.length == 3) {
                    String targetIP = parts[1];
                    String message = parts[2];
                    sendDirectMessage(targetIP, message);
                } else {
                    sendMessage("잘못된 명령어 형식입니다. /dm [IP] [메시지]");
                }
            } else if (command.startsWith("/file")) {
                String[] parts = command.split(" ", 3);
                if (parts.length == 3) {
                    String targetIP = parts[1];
                    String filePath = parts[2];
                    sendFile(targetIP, filePath);
                } else {
                    sendMessage("잘못된 명령어 형식입니다. /file [IP] [파일경로]");
                }
            } else {
                sendMessage("알 수 없는 명령어입니다. /help를 입력하여 명령어 목록을 확인하세요.");
            }
        }

        private void sendDirectMessage(String targetIP, String message) {
            for (ClientHandler client : clients) {
                if (client.getClientIP().equals(targetIP)) {
                    client.sendMessage("[DM from " + getClientIP() + "]: " + message);
                    return;
                }
            }
            sendMessage("해당 IP 주소를 가진 클라이언트를 찾을 수 없습니다.");
        }

        private void sendFile(String targetIP, String filePath) {
            File file = new File(filePath);
            if (!file.exists() || !file.isFile()) {
                sendMessage("파일을 찾을 수 없습니다.");
                return;
            }

            if (targetIP.isEmpty()) {
                // 모든 클라이언트에게 파일 전송 요청
                String fileInfo = getClientIP() + "," + file.getName() + "," + file.length();
                for (ClientHandler client : clients) {
                    if (client != this) {
                        client.sendMessage("[파일 전송 요청] " + fileInfo + "을 받으시겠습니까? (Y/N)");
                    }
                }
                return;
            }

            for (ClientHandler client : clients) {
                if (client.getClientIP().equals(targetIP)) {
                    client.sendMessage("[파일 전송 요청] " + getClientIP() + "에서 " + file.getName() + "을 받으시겠습니까? (Y/N)");
                    // 파일 전송 요청 응답 처리
                    String response;
                    try {
                        response = reader.readLine();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    if (response.equalsIgnoreCase("Y")) {
                        client.sendFile(targetIP, filePath);
                        sendMessage("[Server] 파일 전송이 완료되었습니다.");
                    } else {
                        sendMessage("[Server] " + client.getClientIP() + "님이 파일 전송을 거부했습니다.");
                    }
                    return;
                }
            }
            sendMessage("해당 IP 주소를 가진 클라이언트를 찾을 수 없습니다.");
        }

        private void handleFileTransferRequest(String[] commandArgs) {
            if (commandArgs.length < 3) {
                sendMessage("[Server] 파일 전송 요청 형식이 올바르지 않습니다.");
                return;
            }

            String senderIP = commandArgs[0];
            String fileName = commandArgs[1];
            long fileSize = Long.parseLong(commandArgs[2]);

            sendMessage("[Server] " + senderIP + "로부터 파일을 전송받습니다. 파일명: " + fileName + " (크기: " + fileSize + " bytes)");
            receiveFile(fileName, fileSize);
        }

        // 파일을 서버에 전송받는 메서드
        private void receiveFile(String fileName, long fileSize) {
            String filePath = "./" + fileName;
            try {
                byte[] buffer = new byte[4096];
                FileOutputStream fos = new FileOutputStream(filePath);
                int bytesRead;
                long totalBytesRead = 0;

                while (totalBytesRead < fileSize) {
                    bytesRead = reader.read();
                    if (bytesRead == -1) {
                        break;
                    }
                    fos.write(buffer, 0, bytesRead);
                    totalBytesRead += bytesRead;
                }

                fos.close();
                sendMessage("[Server] 파일 수신이 완료되었습니다.");
            } catch (IOException e) {
                e.printStackTrace();
                sendMessage("[Server] 파일 수신 중 오류가 발생했습니다.");
            }
        }


        @Override
        public void run() {
            try {
                String command;
                while ((command = reader.readLine()) != null) {
                    handleCommand(command);
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                close();
                clients.remove(this);
                broadcastMessage(this, "Client " + getClientIP() + " 접속 종료");
                broadcastClientList();
            }
        }

        private void close() {
            try {
                if (reader != null) {
                    reader.close();
                }
                if (writer != null) {
                    writer.close();
                }
                if (clientSocket != null) {
                    clientSocket.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
