import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import java.util.TreeSet;

public class BlockLSH {

    // ###############################################################################
    // ## The k-value (minimum number of people that should make up a valid cohort) ##
    // ###############################################################################
    public static int kSize = 2000;
    
    public static String myCohortHash = null;
    public static String myCohortID = null;
    
    // #######################################################################
    // ## Subsection of cohort hash to send to others (original is 50 bits) ##
    // #######################################################################
    public static int hashSize = 50;


    /** Calculates a cohort hash based on data provided (e.g. browsing history)
     * @param filename name of the .json file with data
     * @return String value of the hash
     */
    public static String getCohortHash(String filename) {
        String cmd = "go run main.go " + filename;
        String cohort = "";

        try {
            Process p = Runtime.getRuntime().exec(cmd);
            p.waitFor();

            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            cohort = reader.readLine();

        } catch (Exception E) {}

        return getCohortBits(cohort);
    }

    /** Calculates a cohort hash based on data provided (e.g. browsing history)
     * @return String value of the hash
     */
    public static String getCohortHash() {
        String cmd = "go run main.go host_list.json";
        String cohort = "";

        try {
            Process p = Runtime.getRuntime().exec(cmd);
            p.waitFor();

            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            cohort = reader.readLine();

        } catch (Exception E) {}

        return getCohortBits(cohort);
    }

    /** Pads a cohort hash to exactly 50 bits
     * @param cohort Cohort hash to pad
     * @return String value of the padded hash
     */
    public static String getCohortBits(String cohort) {
        return String.format("%50s", Long.toBinaryString((Long.parseLong(cohort)))).replace(' ', '0');
    }

    /** Hashes the transaction object and returns its hash as String (SHA256)
     * @param tx The transaction object we want to hash
     * @return Returns the hash of the transaction object as a String
     */
    public static String hash(Transaction tx) {
        String hashtext;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(tx.toString().getBytes());
            BigInteger numb = new BigInteger(1, bytes);
            hashtext = numb.toString(16);
            while (hashtext.length() < 64) {
                hashtext = "0" + hashtext;
            }
            return hashtext;
        }
        catch(NoSuchAlgorithmException nsae) {
            System.out.println("HASHING ERROR: " + nsae);
            return null;
        }
    }

    /** Mines the transaction object until the difficulty level is satisfied
     * @param tx The unmined transaction object
     * @return confirmation of proof-of-work
     */
    public static boolean mineTransaction(Transaction tx) {
        String target = new String(new char[Miner.txDif]).replace('\0', '0');
        System.out.println("target: " + target);
        System.out.println("starting to mine..");

        // Increase nonce value until the proof-of-work challenge is solved..
        while (!hash(tx).substring(0, Miner.txDif).equals(target)) {
            tx.nonce++;
        }
        System.out.println("mined tx: " + hash(tx));
        System.out.println("finished mining tx..");
        return true;
    }

    /** Calculates the cohort ID to be used when browsing, based on PrefixLSH
     * ( https://www.chromium.org/Home/chromium-privacy/privacy-sandbox/floc/ )
     * @param txs List of all transactions in a block
     * @param myCohort Users own cohort hash vaue
     * @return users cohort ID or 'null'
     */
    public static String getCohortID(TreeSet<Transaction> txs, String myCohort) {
        int prefixSize = 0;
        String prefix = myCohort.substring(0, prefixSize);
        String cohort;

        if(txs.isEmpty()) {
            System.out.println("txs empty");
            cohort = "null";
        }

        else {
            Iterator<Transaction> it = txs.iterator();
            TreeSet<String> cohorts = new TreeSet<>();

            int saiz = 0;

            while (it.hasNext()) {
                String tx = it.next().getCohort();
                if (tx.startsWith(prefix)) {
                    cohorts.add(tx);
                }
            }

            for (; (cohorts.size() >= kSize) || (prefixSize==50); prefixSize += 1) {
                Iterator<String> it2 = cohorts.iterator();
                TreeSet<String> cohorts2 = new TreeSet<>();
                prefix = myCohort.substring(0, prefixSize);

                while (it2.hasNext()) {
                    String tx = it2.next();
                    if (tx.startsWith(prefix)) {
                        cohorts2.add(tx);
                    }
                }
                saiz = cohorts.size();
                cohorts = (TreeSet<String>) cohorts2.clone();
            }

            cohort = myCohort.substring(0, prefixSize);
            Main.kai.add(saiz);
        }

        if(prefixSize-1 <= 0) {
            cohort = "null";
        }


        if(new File("myCohortID").exists()) {
            new File("myCohortID").delete();
        }

        try {
            FileOutputStream fos = new FileOutputStream("myCohortID");
            fos.write(cohort.getBytes(StandardCharsets.UTF_8));

            fos.close();
            myCohortID = cohort;
            System.out.println("cohordID saved to file");
        } catch (Exception e) {
            System.out.println("Saving cohort to file error; " + e);
        }

        return cohort;
    }

    /** Creates a Transaction object and broadcasts it to other peers in the network
     * @param n The Network object that the user is connected on
     */
    public static void sendTransaction(Network n) {
        // Create object and transmit it
        String fullHash = BlockLSH.getCohortHash();
        String minHash = fullHash.substring(0, hashSize);
        Transaction tx = new Transaction(minHash);

        System.out.println("init: " + tx.toString());

        // mine until finished
        while(!mineTransaction(tx));

        System.out.println("done: " + tx.toString());

        // send off tx to network
        n.announce(tx);
        System.out.println("cohort tx sent!");

        myCohortHash = fullHash;

        // Save object to our own unconfirmed txs file
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(n.getTxFile(), true))) {
            oos.writeObject(tx);
            System.out.println("TX ADD SAVED: " + tx.toString());
        } catch (Exception e) {
            System.out.println("TX ADD SAVE ERROR: " + e);
        }

        // Save own transaction object (with full version of hash)
        if(new File("myCohortObj").exists()) {
            new File("myCohortObj").delete();
        }

        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream("myCohortObj", false))) {
            tx.setCohort(fullHash);
            oos.writeObject(tx);
            System.out.println("TX SAVED: " + tx.toString());
        } catch (Exception e) {
            System.out.println("TX SAVE ERROR: " + e);
        }

        // TODO; ? if tx not official for a long time, resend it
    }

    /** (**FOR TESTING PURPOSES**) Creates a Transaction object and broadcasts it to other peers in the network
     * @param n The Network object that the user is connected on
     */
    public static void sendTransaction(Network n, Transaction tx) {
        System.out.println("init: " + tx.toString());

        // mine until finished
        while(!mineTransaction(tx));

        System.out.println("done: " + tx.toString());

        // send off tx to network
        n.announce(tx);
        System.out.println("cohort id sent!");

        // Save object to our own unconfirmed txs file
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(n.getTxFile(), true))) {
            oos.writeObject(tx);
            System.out.println("TX SAVED: " + tx.toString());
        } catch (Exception e) {
            System.out.println("TX SAVE ERROR: " + e);
        }
    }

    // reads cohort ID from file
    public static String readCohortID() {
        String id = null;
        try {
            // Open file
            FileInputStream fis = new FileInputStream("myCohortID");
            BufferedReader br = new BufferedReader(new InputStreamReader(fis));

            StringBuilder sb = new StringBuilder();
            String line;
            while(( line = br.readLine()) != null) {
                sb.append( line );
            }
            id = sb.toString();

            myCohortID = id;
        } catch(Exception e) {
            System.out.println("(menu) COHORT FETCHING ERROR: " + e);
        }

        return id;
    }

}
