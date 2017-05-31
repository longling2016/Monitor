import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Created by longlingwang on 5/31/17.
 */
public class Broadcast {
    Address[] addressBook;

    public Broadcast(Address[] addressBook) {
        this.addressBook = addressBook;
    }

    public boolean broadcast(String message) {
        try {
            for (Address each : addressBook) {
                Socket s = new Socket();
                s.connect(new InetSocketAddress(each.ip, each.port), 500);
                DataOutputStream out = new DataOutputStream(s.getOutputStream());
                out.writeUTF(message);
                out.flush();
                out.close();
                s.close();
            }
        } catch (IOException e) {
            return false;
        }
        return true;
    }
}