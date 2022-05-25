import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

public class Encryption {
    /** Returns users public key
     * @return Returns the users public key as a Key object
     */
    public Key getRSAPublic() {
        try {
            File f = new File("publicKey");
            FileInputStream fis = new FileInputStream(f);
            DataInputStream dis = new DataInputStream(fis);
            byte[] keyBytes = new byte[(int)f.length()];
            dis.readFully(keyBytes);
            dis.close();
            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return kf.generatePublic(spec);
        } catch(Exception e) {
            System.out.println("GET RSA PRIV ERROR: " + e);
            return null;
        }
    }

    /** Returns users private key
     * @return Returns the users private key as a Key object
     */
    private Key getRSAPrivate() {
        try {
            File f = new File("privateKey");
            FileInputStream fis = new FileInputStream(f);
            DataInputStream dis = new DataInputStream(fis);
            byte[] keyBytes = new byte[(int) f.length()];
            dis.readFully(keyBytes);
            dis.close();
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return kf.generatePrivate(spec);
        } catch(Exception e) {
            System.out.println("GET RSA PUB ERROR: " + e);
            return null;
        }
    }
}
