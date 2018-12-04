import java.io.*;
import java.net.*;
import java.util.Scanner;

public class ProjesterClient {

    private static PrintWriter out;
    private static Socket serverSocket;
    private static BufferedReader in;
    private static String outputFromServer;
    private static String userInput = "";
    private static String nick;
    private static String room;
    private static int port;

    public static void main(String[] args) {
        System.out.println("Client started...");

        // Add parameters:
        // Run / Edit configurations -> program arguments

        // hardcoded default server port
        port = 12314;

        // initialize parameters
        String choice;
        String host;
        if(args.length == 4){
            host = args[0];
            nick = args[1];
            room = args[2];
            choice = args[3];
        }else{
            System.err.println("Wrong arguments");
            return;
        }

        // check parameters
        if(!(choice.equals("chat") || choice.equals("file"))){
            System.err.println("Wrong value of choice (chat,file - " + choice + ")");
            return;
        }

        // connection protocol
        System.out.println("Starting connection protocol:");
        try {
            // get room data
            serverSocket = new Socket(host, port);
            out = new PrintWriter(serverSocket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(serverSocket.getInputStream()));

            // send nick
            System.out.println("Sending nick...");
            sendWord(nick, 32);
            System.out.println("Nick sent successfully.");

            // send room
            System.out.println("Sending room...");
            sendWord("12", 32);
            System.out.println("Room sent successfully.");

            // check if nick hasn't been taken
            outputFromServer = in.readLine();
            if(outputFromServer.contains("This nick has already been taken")){
                System.err.println("This nick has already been taken, please try again");
                System.exit(1);
            }else if(outputFromServer.equals("No Room is available right now, try again later")){
                System.err.println("No Room is available right now, try again later");
                System.exit(1);
            }

            System.out.println("Waiting for port...");
            port = Integer.parseInt(outputFromServer);
            System.out.println("Port received: "+ port);

            // connect to the room
            System.out.println("Connecting to the room...");
            serverSocket = new Socket(host, port);
            System.out.println("Socket passed");
            in = new BufferedReader(new InputStreamReader(serverSocket.getInputStream()));
            System.out.println("New input stream passed");
            out = new PrintWriter(serverSocket.getOutputStream(), true);
            System.out.println("New output stream passed");
            System.out.println("Connected to the room successfully.");

            // send choice
            System.out.println("Sending choice...");
            sendWord(choice, 32);
            System.out.println("Wait for server response...");

            //check choice
            outputFromServer = in.readLine();
            if(!outputFromServer.equals("accepted")){
                System.err.println("Wrong choice - send: "+ outputFromServer);
                System.exit(1);
            }
            System.out.println("Choice accepted.");

            switch(choice){
                case "chat":
                    // start asynchronous chat
                    System.out.println("Starting asynchronous chat...");
                    Input threadInput = new Input();
                    threadInput.start();
                    System.out.println("Asynchronous INPUT running...");

                    // start asynchronous output
                    Output threadOutput = new Output();
                    threadOutput.start();
                    System.out.println("Asynchronous OUTPUT running...");

                    break;
                case "file":
                    //start file service
                    Scanner input = new Scanner(System.in);
                    System.out.println("File console activated. Please choose form of transfer and file name:");
                    while(!(userInput = input.nextLine()).equals("@exit")){
                        switch (userInput.split(" ")[0]) {
                            case "@upload":
                                try {
                                    uploadFile(userInput.split(" ")[1]);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                                break;
                            case "@download":
                                downloadFile(userInput.split(" ")[1]);
                                break;
                            default:
                                System.err.println("Wrong input. Choose one:");
                                System.err.println("@upload filename");
                                System.err.println("@download filename");
                                System.err.println("@exit");
                                break;
                        }
                        System.out.println("File console activated. Please choose form of transfer and file name:");
                    }
                    break;
                default: System.err.println("Wrong choice"); break;
            }
            System.out.println("Client finished his job for now.");
        } catch (UnknownHostException e) {
            System.err.println("Could not recognize host " + host);
        } catch (IOException e) {
            System.err.println("Could not connect with host " + host);
        }
    }

    // asynchronous client server code:
    // https://stackoverflow.com/questions/32577980/asynchronous-client-side-java-socket
    static class Output extends Thread {
        public void run(){
            try {
                // receive messages
                while ((outputFromServer = in.readLine()) != null) {
                    outputFromServer = outputFromServer.trim();
                    if(!outputFromServer.startsWith(nick)){
                        System.out.println(outputFromServer.trim());
                    }
                }
            }catch (SocketException e){
                System.err.println("Connection to the host was lost");
            } catch (IOException e) {
                System.err.println("Asynchronous OUTPUT failed!");
                e.printStackTrace();
            }
            System.out.println("Asynchronous OUTPUT closed successfully");
            // close input - REPAIR

        }
    }

    static class Input extends Thread {
        public void run(){

            BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in));
            try {
                sendWord("chat", 32);
                // send greetings
                sendWord(nick + " has joined!", 256);
                // start chat
                System.out.println("Welcome to the room "+room + " at port " + port);
                while ((userInput = stdIn.readLine()) != null) {
                    sendWord(nick + ": " + userInput, 256);
                }
            } catch (IOException e) {
                System.err.println("Asynchronous INPUT failed!");
                System.err.println("Exception during communication. Server probably closed connection.");
                e.printStackTrace();
            }
            System.out.println("Asynchronous INPUT closed successfully");
            // close output - REPAIR

        }
    }

    private static void sendWord(String word, int length) {
        // control text length
        if (word.length() < length){
            // translate string to char array
            char[] array = new char[length];
            StringReader arrayReader = new StringReader(word);
            try {
                arrayReader.read(array); //Reads string into the array. Throws IOException
            } catch (IOException e) {
                System.err.println("Translation of " + word + " (length " + word.length() + ") to " + length + " chars array failed!");
                e.printStackTrace();
                System.exit(1);
            }
            // send to host
            out.println(array);
        }else{
            System.err.println("Oversized text (" + word + "), " +
                    "size: "+ word.length() + ", expected: "+ length + "");
        }
    }

    // https://stackoverflow.com/questions/27498845/file-transfer-between-java-client-and-c-server
    private static void uploadFile(String fileName) throws FileNotFoundException {
        
        System.out.println("Initializing file " + fileName + " upload...");

        // send choice
        sendWord("upload", 32);
        File file = new File(fileName);
        //send file path
        sendWord(fileName, 32);

        // initialize file
        FileInputStream fis;
        fis = new FileInputStream(file);
        System.out.println("File size: "+ file.length() + " bytes");

        // send file size and set bytes array to it
        System.out.println("Sending file size...");
        sendWord(Long.toString(file.length()), 32);
        System.out.println("File size sent successfully");

        byte[] bytes = new byte[1024];

        // send bytes
        System.out.println("Initializing stream...");
        BufferedInputStream bis = new BufferedInputStream(fis);
        OutputStream outStream = null;
        System.out.println("Stream initialized");

        try {
            outStream = serverSocket.getOutputStream();
        } catch (IOException e) {
            System.err.println("Connection with host was lost");
            e.printStackTrace();
        }

        System.out.println("Sending file...");
        int count, i=1;
        try {
            while ((count = bis.read(bytes)) > 0) {
                assert outStream != null;
                outStream.write(bytes, 0, count);
                System.out.println("Package " + i + " sent");i++;
            }
        } catch (IOException e) {
            System.err.println("Connection with host was lost");
            e.printStackTrace();
        }
        System.out.println("Closing subprocedures...");

        // close subprocedure
        try{
            assert outStream != null;
            outStream.flush();
            outStream.close();
            fis.close();
            bis.close();
        } catch (IOException e) {
            System.err.println("Subprocedure closing failed!");
            e.printStackTrace();
        }
        System.out.println("Subprocedure closed");

        // wait for confirmation
        System.out.println("Waiting for confirmation from server...");
        confirmation();
        if(outputFromServer.equalsIgnoreCase("accepted")){
            System.out.println("File upload successful.");
        }else{
            System.out.println("Confirmation failed - received: "+ outputFromServer);
        }
    }

    // https://stackoverflow.com/questions/9520911/java-sending-and-receiving-file-byte-over-sockets
    private static void downloadFile(String fileName) {
        System.out.println("Initializing file " + fileName + " download...");

        // send choice
        sendWord("download", 32);
        //send file path
        sendWord(fileName, 32);

        // get file size
        System.out.println("Sending file size...");
        try {
            outputFromServer = in.readLine();
        } catch (IOException e) {
            System.err.println("File size receiveing failed - received: " + outputFromServer);
            e.printStackTrace();
        }
        int filesize = Integer.parseInt(outputFromServer);
        System.out.println("File size received successfully");
        System.out.println("File size: "+ filesize + " bytes");

        InputStream inStream;
        inStream = null;
        OutputStream outStream;
        outStream = null;

        try {
            inStream = serverSocket.getInputStream();
        } catch (IOException ex) {
            System.out.println("Can't get socket input stream. ");
        }

        try {
            outStream = new FileOutputStream(fileName);
        } catch (FileNotFoundException ex) {
            System.out.println("File not found. ");
        }

        byte[] bytes = new byte[1024];

        // receive file
        System.out.println("Receiving file...");
        int count, i = 1;
        try {
            assert inStream != null;
            while ((count = inStream.read(bytes)) > 0) {
                assert outStream != null;
                outStream.write(bytes, 0, count);
                System.out.println("Package " + i + " received");i++;
            }
        } catch (IOException e) {
            System.err.println("Connection with host was lost");
            e.printStackTrace();
        }
        System.out.println("Closing subprocedures...");

    // close subprocedure
        try{
            assert outStream != null;
            outStream.close();
            inStream.close();
       } catch (IOException e) {
            System.err.println("Subprocedure closing failed!");
            e.printStackTrace();
        }
            System.out.println("Subprocedure closed");

        // wait for confirmation
            System.out.println("Waiting for confirmation from server...");
            confirmation();
            if(outputFromServer.equalsIgnoreCase("accepted")){
                System.out.println("File upload successful.");
            }else System.out.println("Confirmation failed - received: " + outputFromServer);
    }

    private static void confirmation(){
        try {
            outputFromServer = in.readLine();
        } catch (SocketException e) {
            System.out.println("Closed connection");
        } catch (IOException e) {
            System.err.println("Confirmation  failed!");
            e.printStackTrace();
        }
    }
}