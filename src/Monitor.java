import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.Scanner;

/**
 * Created by longlingwang on 5/31/17.
 */
public class Monitor {
    static Address[] addressBook;

    public static void main(String[] args) {
        String ip = "";
        int port = 0;
        Broadcast bc = new Broadcast(addressBook);

        try {
            ip = InetAddress.getLocalHost().getHostAddress();

            ServerSocket ss = new ServerSocket(0);
            port = ss.getLocalPort();

            Scanner scanner = new Scanner(System.in);

            Thread thread = new Thread(new ListeningThread(ss));
            thread.start();

            //  prompt for the user's command
            System.out.print("Monitor: Please add nodes' ip and port> ");

            addressBook = getAddressBook(scanner.nextLine());

        } catch (IOException e) {
            System.out.println(e);
        }

        if (!bc.broadcast("moni" + ip + " " + port)){
            System.out.println("Something wrong with user input for nodes information! Please restart.");
            System.exit(1);
        }

        bc.broadcast("book" + bookToString());

    }

    private static Address[] getAddressBook (String list) {
        String[] addrs = list.split(", ");
        Address[] book = new Address[addrs.length];
        for (int i = 0; i < addrs.length; i ++ ) {
            String[] info = addrs[i].split(" ");
            book[i] = new Address(i, info[0], Integer.parseInt(info[1]));
        }
        return book;
    }

    private static String bookToString() {
        StringBuilder sb = new StringBuilder();
        for (Address each: addressBook) {
            sb.append(each.hostID + " " + each.ip + " " + each.port + ",");
        }
        return sb.toString();
    }

}
