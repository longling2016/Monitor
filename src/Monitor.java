import java.io.IOException;
import java.math.RoundingMode;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.text.DecimalFormat;
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
    static ArrayList<String> values = new ArrayList<>();
    static ArrayList<Integer> blocks = new ArrayList<>();

    static final int totalWritingTime = 10;  // TODO: can be modified
    static final int writingQ = 3; // TODO: keep same as node class


    public static void main(String[] args) {

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

            if (!bc.broadcast("moni" + ip + " " + port)) {
                System.out.println("Something wrong with user input for nodes information! Please restart.");
                System.exit(1);
            }

            System.out.print("Monitor: add nodes' ip and port for Nodes communication> ");
            message = scanner.nextLine();
            bc.broadcast("book" + message);


            // start testing on no-phase protocol
            bc.broadcast("noP");
            System.out.println("Testing on No-Phase Protocol");
            writeReadTrigger();

            // start testing on two-phase protocol
            bc.broadcast("twoP");
            System.out.println("Testing on Two-Phase Protocol");
            writeReadTrigger();

            // start testing on three-phase protocol
            bc.broadcast("threeP");
            System.out.println("Testing on Three-Phase Protocol");
            writeReadTrigger();


        } catch (IOException e) {
            System.out.println(e);
        }
    }

    private static void writeReadTrigger() {

        Random rand = new Random();
        int runningCounter = 0;
        MessageSender ms = new MessageSender();
        int totalFail = 0;
        int consistentReading = 0;

        System.out.println(String.join("", Collections.nCopies(300, "-")));

        int i = 1;
        while (i <= addressBook.length) {
            System.out.print(String.format("%-10s", "Node" + i));
            i ++;
        }
        System.out.print(String.format("%-20s", "If consistent?"));
        System.out.print(String.format("%-20s", "If success?"));
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

            runningCounter++;

            // make sure everyone is online, do reading
            testCrash();
            bc.broadcast("read");

            // print reading result and record
            while (values.size() < addressBook.length) {
                // do nothing and wait
            }

            int[] valueArray = new int[addressBook.length];
            int counter = 0;
            for (String each : values) {
                int curValue = Integer.parseInt(each.split(":")[1]);
                if (curValue == value) {
                    counter++;
                }
                valueArray[Integer.parseInt(each.split(":")[0])] = curValue;
            }
            values.clear();

            for (int each : valueArray) {
                System.out.print(String.format("%-10s", each));
            }

            if (status == 0) { // fail
                System.out.print(String.format("%-20s", "Fail"));
                totalFail++;
            } else { //success
                System.out.print(String.format("%-20s", "Success"));
            }
            status = -1;

            if (counter == writingQ) {
                System.out.print(String.format("%-20s", "Yes"));
                consistentReading++;
            } else {
                System.out.print(String.format("%-20s", "No"));
            }
        }

        final long endTime = System.currentTimeMillis();

        System.out.println("\n\n\n");
        System.out.println(String.join("", Collections.nCopies(300, "-")));

        System.out.println("Total execution time: " + (endTime - startTime) );
        System.out.println("Total writing operations: " + totalWritingTime);
        System.out.println(String.join("", Collections.nCopies(300, "-")));

        DecimalFormat df = new DecimalFormat("#.##");

        System.out.println("Failed writing due to crash: " + totalFail);
        System.out.println("Fail rate: " + df.format((double)totalFail / totalWritingTime) + "\n");

        System.out.println("Consistency rate: " + df.format((double)consistentReading / totalWritingTime));
        bc.broadcast("block?");

        while (blocks.size() < addressBook.length) {
            // waiting
        }
        int max = 0;
        for (int each: blocks) {
            if (each > max) {
                max = each;
            }
        }
        blocks.clear();
        System.out.println("Blocking times: " + max);
        System.out.println(String.join("", Collections.nCopies(300, "-")) + "\n\n");


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
        } else if (message.length() > 4 && message.substring(0, 5).equals("value")) {
            values.add(message.substring(5, message.length()));
        } else if (message.length() > 4 && message.substring(0, 5).equals("block")) {
            blocks.add(Integer.parseInt(message.substring(5, message.length())));
        } else {
            System.out.println("Received wrong message: " + message);
        }

    }

}
