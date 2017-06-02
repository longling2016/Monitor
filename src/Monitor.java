import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;
import java.util.Scanner;

/**
 * Created by longlingwang on 5/31/17.
 */
public class Monitor {
    static Address[] addressBook;
    static int ackCounter = 0;
    static int crashCounter = 0;
    static int status = -1;
    static Broadcast bc;
    static ArrayList<Integer> values = new ArrayList<>();


    public static void main(String[] args) {
        int totalNodes = 5;  // TODO: can be modified
        int totalWritingTime = 10;  // TODO: can be modified
        int totalFail = 0;
        int consistentReading = 0;


        try {
            String ip = InetAddress.getLocalHost().getHostAddress();

            ServerSocket ss = new ServerSocket(0);
            int port = ss.getLocalPort();

            Scanner scanner = new Scanner(System.in);

            Thread thread = new Thread(new ListeningThread(ss));
            thread.start();

            //  prompt for the user's command
            System.out.print("Monitor: add nodes' ip and port for Monitor> ");

            String message = scanner.nextLine();

            addressBook = getAddressBook(message);

            bc = new Broadcast(addressBook);

            if (!bc.broadcast("moni" + ip + " " + port)){
                System.out.println("Something wrong with user input for nodes information! Please restart.");
                System.exit(1);
            }

            System.out.print("Monitor: add nodes' ip and port for Nodes communication> ");
            message = scanner.nextLine();
            bc.broadcast("book" + message);

            Random rand = new Random();
            int runningCounter = 0;
            MessageSender ms = new MessageSender();

            // start testing on no-phase protocol
            bc.broadcast("noP");
            System.out.println("Testing on No-Phase Protocol");
            System.out.println(String.join("", Collections.nCopies(100, "-")));

            System.out.print(String.format("%-10s", "If success?"));
            int i = 1;
            while (i <= totalNodes) {
                System.out.print(String.format("%-10s", "Node" + i));
                i ++;
            }
            System.out.println();

            final long startTime = System.currentTimeMillis();
            while (runningCounter < totalWritingTime) {
                testCrash();
                int node = rand.nextInt(addressBook.length);
                int value = rand.nextInt(10000);
                ms.send("write" + value, addressBook[node].ip, addressBook[node].port);
                while (status == -1) {
                    // do nothing and wait
                }
                if (status == 0) { // fail
                    System.out.print(String.format("%-10s", "Fail"));
                    totalFail ++;
                } else { //success
                    System.out.print(String.format("%-10s", "Success"));
                }

                runningCounter ++;

                // make sure everyone is online, do reading
                testCrash();
                bc.broadcast("read");

                // print reading result and record






            }


            // start testing on two-phase protocol
            bc.broadcast("twoP");

            // start testing on three-phase protocol
            bc.broadcast("threeP");



        } catch (IOException e) {
            System.out.println(e);
        }






    }

    private static Address[] getAddressBook (String list) {
        String[] addrs = list.split(",");
        Address[] book = new Address[addrs.length];
        for (int i = 0; i < addrs.length; i ++ ) {
            String[] info = addrs[i].split(" ");
            book[i] = new Address(i, info[0], Integer.parseInt(info[1]));
        }
        return book;
    }

    private static void testCrash() { // test if all nodes are online
        while (true) {
            ackCounter = 0;
            crashCounter = 0;

            bc.broadcast("ping");
            while (ackCounter + crashCounter < addressBook.length) {
                // do nothing and wait
            }
            if (crashCounter > 0) {
                // some nodes are crashed. Wait and test again
                try {
                    Thread.currentThread().sleep(100);
                    continue;
                } catch (InterruptedException e) {
                    System.out.println(e);
                }
            } else {
                break;
            }
        }
    }

    public static void listen(String message) {

        // receive ack from nodes
        if (message.equals("ack")) {
            ackCounter ++;
        } else if (message.equals("crash")) {
            crashCounter ++;
        } else if (message.equals("success")) {
            status = 1;
        } else if (message.equals("fail")) {
            status = 0;
        }

    }


}
