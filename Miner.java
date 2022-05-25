import java.io.*;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class Miner {
    public TreeSet<Transaction> txs = new TreeSet<Transaction>();
    private int numTransactions = 0;

    // ##################################################
    // ## min. difficulty for valid transaction object ##
    // ##################################################
    public static int txDif = 2;

    /** Reads transactions from file and adds them to a TreeSet
     * @param f The file that contains the transactions */
    public void fetchTransactions(File f) {
        try {
            // Open file
            FileInputStream fis = new FileInputStream(f);
            ObjectInputStream ois = new ObjectInputStream(fis);
            System.out.println("opened 1");

            // add tx, ensuring no duplicates
            Object obj = null;
            while((obj = ois.readObject()) != null) {
                Transaction tx = (Transaction)obj;
                if(!txs.contains(tx))  {
                    txs.add(tx);
                }
                ois = new ObjectInputStream(fis);
            }

            fis.close();
        } catch(EOFException eof) {
            System.out.println("EOF ERROR: " + eof);

            // Update number of tx in file (error validation)
            numTransactions = txs.size();

        } catch(Exception e) {
            System.out.println("FETCHING ERROR: " + e);
        }
    }

    /** Hashes a block and returns its hash as String (SHA256)
     * @param b The block object we want to hash
     * @return Returns the hash of the block object as a String
     */
    public String hash(Block b) {
        String hashtext;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(b.toString().getBytes());
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

    /** Hashes a block header and returns its hash as String (SHA256)
     * @param header The block header object we want to hash
     * @return Returns the hash of the block header object as a String
     */
    public String hash(indexBlock header) {
        String hashtext;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(header.toString().getBytes());
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

    /** Hashes a String and returns its hash as String (SHA256)
     * @param s The String we want to hash
     * @return Returns the hash of the String object as a String
     */
    public String hash(String s) {
        String hashtext;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(s.getBytes());
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

    /** Hashes a block header and returns its hash as String (SHA256)
     * @param msg The block header object we want to hash
     * @return Returns the hash of the block header object as a String
     */
    public String hash(Transaction msg) {
        String hashtext;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(msg.toString().getBytes());
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

    /** Creates a block with the current uncomfirmed txs
     * @param chain The blockchain class object from which we gather information
     * @return Returns the created (**NOT MINED**) block object
     */
    public Block createBlock(Blockchain chain) {
        // Generate merkleroot of all transactions
        String mr = genMerkleRoot(txs);

        // Get current difficulty level
        long difficulty = chain.getDifficulty();

        // Create block
        Block block = new Block(mr, chain.getCurrentIndex()+1, chain.getCurrentHash(), difficulty, chain.getCurrentDifficultyTotal()+difficulty);
        System.out.println("block created: " + block.toString());
        block.setTransactions(txs);

        return block;
    }

    /** Mines the block until the difficulty level is satisfied, then returns the block
     * @param b The unmined block object
     * @return Returns a successfully mined block
     */
    public Block mineBlock(Block b) {
        // formula: (2^256) / (2^ (256-(4*difficultyLevel))) -> avg. number of hashes required..
        long hashRate = b.getDifficultyLevel();

        // Calculate how many 0's the hash should start with based on the current difficulty level
        final int lvl;

        // Minimum 0's is set at 1, else calculate what it should be
        if( ((int) ((Math.log10(hashRate) / Math.log10(2) ) / 4)) < 1) {
            lvl = 1;
        }
        else {
            lvl = (int) ((Math.log10(hashRate) / Math.log10(2) ) / 4);
        }

        String target = new String(new char[lvl]).replace('\0', '0');
        System.out.println("target: " + target);
        System.out.println("starting to mine..");

        // Increase nonce value until the proof-of-work challenge is solved..
        while (!hash(b).substring(0, lvl).equals(target)) {
            String data = ("\n nonce: " + b.nonce + ",         hash: " + hash(b));

            // Append hash info
            System.out.println(data);

            b.nonce++;
        }

        return b;
    }

    /** Checks if the block has been successfully mined
     * @param b The block object we want to validate
     * @return Returns whether the block is successfully mined (true) or not (false)
     */
    public boolean verifyMined(Block b) {
        // formula: (2^256) / (2^ (256-(4*difficultyLevel))) -> avg. number of hashes required..
        long hashRate = b.getDifficultyLevel();

        // Calculate how many 0's the hash should start with based on the current difficulty level (minimum should be 1)
        int lvl = (int) ((Math.log10(hashRate) / Math.log10(2) ) / 4);
        if(lvl < 1) {
            lvl = 1;
        }

        // Check if block is mined
        String target = new String(new char[lvl]).replace('\0', '0');
        if(hash(b).substring(0,lvl).equals(target)) return true;
        else return false;
    }

    /** Checks if the block header has been successfully mined -> creates Block object from header data
     * @param header The block header object we want to validate
     * @return Returns whether the block is successfully mined (true) or not (false)
     */
    public boolean verifyMined(indexBlock header) {
        // Create block object from header data
        Block b = new Block(header.getIndex(), header.getDate(), header.getPrevHash(), header.getNonce(), header.getDifficultyLevel(), header.getMerkleRoot(), header.getTotalDifficulty());

        // formula: (2^256) / (2^ (256-(4*difficultyLevel))) -> avg. number of hashes required..
        long hashRate = b.getDifficultyLevel();

        // Calculate how many 0's the hash should start with based on the current difficulty level (minimum should be 1)
        int lvl = (int) ((Math.log10(hashRate) / Math.log10(2) ) / 4);
        if(lvl < 1) {
            lvl = 1;
        }

        // Check if block is mined
        String target = new String(new char[lvl]).replace('\0', '0');
        if(hash(b).substring(0,lvl).equals(target)) return true;
        else return false;
    }

    /** Checks if the transaction has been successfully mined -> creates Block object from header data
     * @param tx The transaction object we want to validate
     * @return Returns whether the transaction is successfully mined (true) or not (false)
     */
    public boolean verifyMined(Transaction tx) {
        String target = new String(new char[txDif]).replace('\0', '0');
        if(hash(tx).substring(0,txDif).equals(target)) return true;
        else return false;
    }

    /** Hashes the Merkle tree children to make parents
     * @param children The children hashes
     * @return Returns the hashed children pairs
     */
    private LinkedList<String> genParentHash(LinkedList<String> children) {
        LinkedList<String> parents = new LinkedList<>();

        // If number of children is odd, add the last child again to make it even
        if( (children.size() != 1) && (children.size() % 2 != 0) ) {
            children.add(children.getLast());
        }

        // Hash the children pairs to make a new parent, and add it to a new LinkedList
        for(int x = 0; x < children.size(); x=x+2) {
            String left = children.get(x);
            String right = children.get(x+1);

            parents.add(hash(left+right));
        }

        return parents;
    }

    /** Generates MerkleRoot of all transactions
     * @param txs All transactions
     * @return Returns the root hash of the Merkle tree
     */
    public String genMerkleRoot(TreeSet<Transaction> txs) {
        LinkedList<String> hashes = new LinkedList<>();

        // If no messages are present, we have nothing to work with
        if(txs.isEmpty()) {
            return "empty";
        }

        // If number of messages is odd, add the last message again to make it even
        if(txs.size() % 2 != 0) {
            txs.add(txs.last());
        }

        // Hash each message object and add it to a LinkedList
        for (Transaction message : txs) {
            hashes.add(hash(message.toString()));
        }

        // Hash the children pairs until only one hash (the root) remains
        while(hashes.size() != 1) {
            hashes = genParentHash(hashes);
        }

        return hashes.get(0);
    }

    // TODO; ? MerkleTree verification
}