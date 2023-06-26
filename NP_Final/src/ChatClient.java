import java.io.*;
import java.net.*;
import java.util.Scanner;

public class ChatClient {
    private Socket clientSocket;
    private BufferedReader reader;
    private BufferedWriter writer;

    public static void main(String[] args) {
        ChatClient client = new ChatClient();
        client.connect("localhost", 8080);
    }

    public void connect(String serverIP, int serverPort) {
        try {
            clientSocket = new Socket(serverIP, serverPort);
            reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            writer = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));

            Thread receiveThread = new Thread(this::receiveMessages);
            receiveThread.start();

            handleUserInput();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            close();
        }
    }

    private void handleUserInput() {
        Scanner scanner = new Scanner(System.in);
        String command;
        while ((command = scanner.nextLine()) != null) {
            try {
                writer.write(command);
                writer.newLine();
                writer.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void receiveMessages() {
        try {
            String message;
            while ((message = reader.readLine()) != null) {
                System.out.println(message);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            close();
        }
    }

    private void handleServerCommand(String serverMessage) {
        String[] commandArgs = serverMessage.split(" ");
        String command = commandArgs[0];

        switch (command) {

            case "/file":
                handleFileTransferRequest(commandArgs);
                break;

            default:
                System.out.println(serverMessage);
                break;
        }
    }

    private void handleFileTransferRequest(String[] commandArgs) {
        if (commandArgs.length < 4) {
            System.out.println("[Server] 파일 전송 요청 형식이 올바르지 않습니다.");
            return;
        }

        String senderIP = commandArgs[1];
        String fileName = commandArgs[2];
        long fileSize = Long.parseLong(commandArgs[3]);

        System.out.println("[Server] " + senderIP + "로부터 파일을 전송받습니다. 파일명: " + fileName + " (크기: " + fileSize + " bytes)");

        // 파일 수신
        receiveFile(fileName, fileSize);

        // 파일 수신 완료 메시지 출력
        System.out.println("[Server] 파일 수신이 완료되었습니다.");
    }

    private void receiveFile(String fileName, long fileSize) {
        String filePath = "C:"+ File.pathSeparator + "Users"+ File.pathSeparator + "skarj"+ File.pathSeparator + " Desktop"+ File.pathSeparator + "New File" + File.pathSeparator + fileName;
        try {
            FileOutputStream fos = new FileOutputStream(filePath);
            byte[] buffer = new byte[4096];
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
        } catch (IOException e) {
            e.printStackTrace();
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
