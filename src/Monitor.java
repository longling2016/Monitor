import java.io.IOException;
import java.math.RoundingMode;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by longlingwang on 5/31/17.
 */
public class Monitor {
    static Address[] addressBookMonitor;
    static Address[] addressBook;
    static int ackCounter = 0;
    static int crashCounter = 0;
    static int status = -1;
    static Broadcast bc;
    static ArrayList<String> values = new ArrayList<>();
    static ArrayList<Integer> blocks = new ArrayList<>();
    static int writingQ;
    static final Object trigger = new Object();

    static final int totalWritingTime = 10;  // TODO: can be modified



    public static void main(String[] args) {

        try {
            String ip = InetAddress.getLocalHost().getHostAddress();

            ServerSocket ss = new ServerSocket(0);
            int port = ss.getLocalPort();

            Scanner scanner = new Scanner(System.in);

            Thread thread = new Thread(new ListeningThread(ss));
            thread.start();

            //  prompt for the user's command
            System.out.print("Monitor: indicate writing quorum> ");
            String message = scanner.nextLine();

            writingQ = Integer.parseInt(message);

            System.out.print("Monitor: add nodes' ip and port for Monitor> ");

            message = scanner.nextLine();

            addressBookMonitor = getAddressBook(message);

            bc = new Broadcast(addressBookMonitor);

            if (!bc.broadcast("moni" + ip + " " + port)) {
                System.out.println("Something wrong with user input for nodes information! Please restart.");
                System.exit(1);
            }

            bc.broadcast("quorum" + writingQ);

            System.out.print("Monitor: add nodes' ip and port for Nodes communication> ");
            message = scanner.nextLine();
            addressBook = getAddressBook(message);
            bc.broadcast("book" + message);


            // start testing on no-phase protocol
//            bc.broadcast("noP");
//            System.out.println("\n\nTesting on No-Phase Protocol");
//            writeReadTrigger(0);
//            bc.broadcast("end");

//            // start testing on two-phase protocol
            bc.broadcast("twoP");
            System.out.println("\n\nTesting on Two-Phase Protocol");
            writeReadTrigger(1);
            bc.broadcast("end");

//
//            // start testing on three-phase protocol
//            bc.broadcast("threeP");
//            System.out.println("\n\nTesting on Three-Phase Protocol");
//            writeReadTrigger(1);
//            bc.broadcast("end");



        } catch (IOException e) {
            System.out.println(e);
        }
    }

    private static void writeReadTrigger(int phase) {

        Random rand = new Random();
        int runningCounter = 0;
        MessageSender ms = new MessageSender();
        int totalFail = 0;
        int consistentReading = 0;
        int failWriteTotalTime = 0;
        int successWriteTotalTime = 0;

        System.out.println(String.join("", Collections.nCopies(100, "-")));

        int i = 1;
        while (i <= addressBook.length) {
            System.out.print(String.format("%-10s", "Node" + i));
            i ++;
        }
        System.out.print(String.format("%-20s", "If success?"));
        System.out.print(String.format("%-20s", "If consistent?"));
        System.out.println();


        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            System.out.println(e);
        }


        while (runningCounter < totalWritingTime) {
            testCrash();
            int node = rand.nextInt(addressBook.length);
            int value = rand.nextInt(10000);
            long startTime = 0;
            long endTime = 0;

            if (phase == 0) {
                startTime = System.currentTimeMillis();
                ms.send("write" + value, addressBook[node].ip, addressBook[node].port, 0);

                synchronized(trigger) {
                    try {
                        while (status == -1) {
                            trigger.wait();
                        }
                    } catch (InterruptedException e) {
                        System.out.println(e);
                    }
                }
                endTime = System.currentTimeMillis();

            } else {

                startTime = System.currentTimeMillis();
                String res = ms.send("write," + value, addressBook[node].ip, addressBook[node].port, 1);
                endTime = System.currentTimeMillis();

                System.out.println("get response: " + res);

                if (res.equals("success")) {
//            System.out.println("set to success")
                    status = 1;

                } else if (res.equals("fail")) {
//            System.out.println("set to fail");
                    status = 0;

                } else {
                    System.out.println("Error: status = " + status);

                }
            }

            runningCounter++;

            // make sure everyone is online, do reading
            testCrash();
            bc.broadcast("read");

            // print reading result and record

            synchronized(trigger) {
                try {
                    while (values.size() < addressBook.length) {
                        trigger.wait();
                    }
                } catch (InterruptedException e) {
                    System.out.println(e);
                }
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
                failWriteTotalTime += (endTime - startTime);
            } else { //success
                System.out.print(String.format("%-20s", "Success"));
                successWriteTotalTime += (endTime - startTime);
            }
            status = -1;

            if (counter == writingQ) {
                System.out.print(String.format("%-20s", "Yes") + "\n");
                consistentReading++;
            } else {
                System.out.print(String.format("%-20s", "No") + "\n");
            }
        }

        System.out.println("\n");
        System.out.println(String.join("", Collections.nCopies(100, "-")));

        DecimalFormat df = new DecimalFormat();
        df.setMaximumFractionDigits(4);

        System.out.println("Total writing operations: " + totalWritingTime);
        System.out.println("Failed writing due to crash: " + totalFail);
        System.out.println("Fail rate: " + df.format((double)totalFail / totalWritingTime * 100) + "%");

        System.out.println(String.join("", Collections.nCopies(100, "-")));

        System.out.println("Total execution time: " + (failWriteTotalTime + successWriteTotalTime) );

        if (totalFail == 0) {
            System.out.println("No failed writing. All successful!");
        } else {
            System.out.println("Average execution time of failed writing: " + failWriteTotalTime / totalFail);
        }

        if (totalWritingTime - totalFail == 0) {
            System.out.println("No successful writing. All failed!");
        } else {
            System.out.println("Average execution time of successful writing: " + successWriteTotalTime / (totalWritingTime - totalFail));
        }

        System.out.println(String.join("", Collections.nCopies(100, "-")));

        System.out.println("Consistency reading: " + consistentReading);
        System.out.println("Consistency rate: " + df.format((double)consistentReading / totalWritingTime *100) + "%");
        bc.broadcast("block?");


        synchronized(trigger) {
            try {
                while (blocks.size() < addressBook.length) {
                    trigger.wait();
                }
            } catch (InterruptedException e) {
                System.out.println(e);
            }
        }

        int total = 0;
        for (int each: blocks) {
//            if (each > max) {
//                max = each;
//            }
            total += each;
        }
        blocks.clear();
        System.out.println("Blocking times: " + total);
        System.out.println(String.join("", Collections.nCopies(100, "-")) + "\n\n");


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
//            System.out.println("running...");
            bc.broadcast("ping");
//            System.out.println("after broadcast...");


            synchronized(trigger) {
                try {
                    while (ackCounter + crashCounter < addressBook.length) {
//                        System.out.println("inside while loop");
                        trigger.wait();
                    }
                } catch (InterruptedException e) {
                    System.out.println(e);
                }
            }


            if (crashCounter > 0) {
                // some nodes are crashed. Wait and test again
                try {
                    Thread.sleep(100);
                    continue;
                } catch (InterruptedException e) {
                    System.out.println(e);
                }
            } else {
//                System.out.println("inside else...");
                break;
            }
        }

//        System.out.println("return");
    }

    public static void listen(String message) {

        // receive ack from nodes
        if (message.equals("ack")) {
            ackCounter ++;
            if(ackCounter + crashCounter == addressBook.length) {
                synchronized (trigger) {
//                    System.out.println("notify all ack");
                    trigger.notifyAll();
                }
            }

        } else if (message.equals("crash")) {
            crashCounter ++;
            if(ackCounter + crashCounter == addressBook.length) {
                synchronized (trigger) {
//                    System.out.println("notify all crash");
                    trigger.notifyAll();
                }
            }
        } else if (message.length() > 4 && message.substring(0, 5).equals("value")) {
            values.add(message.substring(5, message.length()));
            if (values.size() == addressBook.length) {
                synchronized (trigger) {
                    trigger.notifyAll();
//                    System.out.println("notify all value");

                }
            }

        } else if (message.length() > 4 && message.substring(0, 5).equals("block")) {
            blocks.add(Integer.parseInt(message.substring(5, message.length())));
            if (blocks.size() == addressBook.length) {
                synchronized (trigger) {
                    trigger.notifyAll();
//                    System.out.println("notify all block");

                }
            }

        } else if (message.equals("success")) {
            synchronized(trigger){
                status = 1;
                trigger.notifyAll();
            }

        } else if (message.equals("fail")) {
            synchronized(trigger){
                status = 0;
                trigger.notifyAll();
            }

        } else {
            System.out.println("Received wrong message: " + message);
        }

    }

}
