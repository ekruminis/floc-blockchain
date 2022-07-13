import net.tomp2p.peers.PeerAddress;
import org.apache.commons.lang3.SerializationUtils;
import org.iq80.leveldb.*;
import static org.iq80.leveldb.impl.Iq80DBFactory.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class Blockchain {
    private DB blockDB;
    private DB indexDB;
    private Options options = new Options();
    private int fileNum = 0;

    private long currentIndex = 0;                  // current chain index
    private String currentHash = "GENESIS";         // current chain top block hash
    private long currentDifficultyTotal = 0;        // current chain difficulty

    private indexBlock chainTip = null;      // header of current chain tip

    private LinkedList<unofficial> unofficial = new LinkedList<unofficial>();
    private ArrayList<String> request = new ArrayList<String>();
    private String myPubKey = Base64.getEncoder().encodeToString( Main.encryption.getRSAPublic().getEncoded() );

    // #################################################################################################################
    // ## Number of seconds between a valid cohort hash                                                               ##
    // ## (once expired will no longer be included in cohort ID calculation, users will need to update and resend it) ##
    // #################################################################################################################
    public static long expiredTime = 86400; // 24 hours

    // ####################################################################################################
    // ## Number of seconds between each new block (PoW difficulty will be adjusted based on this value) ##
    // ####################################################################################################
    public static long blockInterval = 3600; // 1 hour


    /** index db store:     f+filename  : indexFile     eg. fblocks0 : file.header
     *                      b+blockhash : indexBlock    eg. b00f3c.. : block.header
     *
     *  block db store:     blockhash   : block
     *                      */

    /** Queries the current chaintip and saves it as a variable */
    public Blockchain() {
        if(chainTip == null) {
            byte[] ct = read(getIndexDB(), "chaintip".getBytes());
            if(ct != null) {
                chainTip = (indexBlock) SerializationUtils.deserialize(ct);
                currentHash = new Miner().hash(chainTip);
                currentIndex = chainTip.getIndex();
                currentDifficultyTotal = chainTip.getTotalDifficulty();
            }
        }
        System.out.println("current chaintip: " + currentHash);
    }

    /** Stores a key:value pair inside a specified database
     * @param database The database we want to use
     * @param key The key of the object as a byte[]
     * @param value The value of the object as a byte[]
     */
    public void add(DB database, byte[] key, byte[] value) {
        try {
            database.put(key, value);
        } finally {
            finish(database);
        }
    }

    /** Returns the value inside a specified database using its key as a byte[]
     * @param database The database we want to use
     * @param key The key of the object as a byte[]
     * @return Returns the value of the object associated with that key */
    public byte[] read(DB database, byte[] key) {
        try {
            return database.get(key);
            // return asString(database.get(key)) -> returns val as a String representation
        } finally {
            finish(database);
        }
    }

    /** Removes the key:value pair inside a specified database
     * @param database The database we want to use
     * @param key The key of the object as a byte[]
     */
    public void remove(DB database, byte[] key) {
        try {
            WriteOptions wo = new WriteOptions();
            database.delete(key, wo);
        } finally {
            finish(database);
        }
    }

    /** Closes the specified database (necessary for multithreading)
     * @param database The database we want to use
     */
    public void finish(DB database) {
        try {
            database.close();
        } catch(IOException ioe) {
            System.out.println("FINISH IOE ERROR: " + ioe);
        }
    }

    /**
     * @return Returns all valid cohorts
     */
    public TreeSet<Transaction> getCohorts() {
        System.out.println("getting all cohorts..");

        TreeSet<Transaction> validCohorts = new TreeSet<>();

        String ch = currentHash;
        boolean late = false;

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        Date currentDate = null;

        try {
            currentDate = sdf.parse(Transaction.makeDate());
        } catch (ParseException e) {
            e.printStackTrace();
        }

        while(!ch.equals("GENESIS") || !late) {
            // read current block
            byte[] contents = read(getBlockDB(), ch.getBytes());

            if(contents == null) {
                //System.out.println("no tx exist in block");
                break;
            }

            else {
                Block b = (Block) SerializationUtils.deserialize(contents);
                System.out.println("got block with hash: " + ch);
                try {
                    // TODO; doublecheck again?
                    if ( ( (currentDate.getTime() - sdf.parse(b.getDate()).getTime() ) / 1000 ) < expiredTime) {
                        // loop over its contents and add valid cohorts
                        for (Transaction tx : b.getTransactions()) {
                            Date msgDate = sdf.parse(tx.getDate());
                            long diffTime = (currentDate.getTime() - msgDate.getTime()) / 1000;

                            if (diffTime < expiredTime) {
                                validCohorts.add(tx);
                            } else {
                                late = true;
                            }
                        }
                    }
                    else {
                        late = true;
                    }
                } catch (Exception e) {
                System.out.println("cohort get date err: " + e);
            }


                // get child block and increase count
                ch = b.getPreviousHash();
            }
        }

        System.out.println("NO. OF VALID COHORTS; " + validCohorts.size());

        return validCohorts;
    }

    /** Saves block inside block DB
     * @param hash The hash of the block we want to store
     * @param block The block object we want to store
     */
    public void storeBlock(String hash, Block block) {
        // TODO; atomicity -> either all changes made or none..
        System.out.println("storing block: " + hash);
        try {

            // Adds block to blockDB if it's not already there
            if( read(getBlockDB(), hash.getBytes()) == null) {
                System.out.println("we don't already have this block..");
                byte[] bytesBlock = SerializationUtils.serialize(block);
                add(getBlockDB(), hash.getBytes(), bytesBlock);
                System.out.println("added to blockDB");
            }
            else{
                System.out.println("we already have this block stored..");
            }

            // Adds block metadata to indexDB if it's not already there
            if(read(getIndexDB(), ("b"+hash).getBytes()) == null) {
                // Adds header to indexDB
                indexBlock blockHeader = new indexBlock(block.getIndex(), block.getDifficultyLevel(), block.getTotalDifficulty(), getFileNum(), block.getDate(), block.getPreviousHash(), block.getMerkleRoot(), block.getNonce());
                System.out.println("header: " + blockHeader.toString());
                byte[] bytesHeader = SerializationUtils.serialize(blockHeader);
                add(getIndexDB(), ("b" + hash).getBytes(), bytesHeader);
                System.out.println("header added to indexDB");

                // Check if this block is a new chain tip/head
                // TODO; ? check if we already have a block in indexFile with higher difficulty
                System.out.println("checking chaintip");
                if(getChainTip() == null) {
                    System.out.println("chaintip is null, so set it..");
                    setChainTip(blockHeader);
                    setCurrentDifficultyTotal(blockHeader.getTotalDifficulty());
                    setCurrentHash(hash);
                    setCurrentIndex(blockHeader.getIndex());

                    TreeSet<Transaction> txs = getCohorts();
                    BlockLSH.getCohortID(txs, BlockLSH.myCohortHash);
                }
                else if(getChainTip().getTotalDifficulty() < blockHeader.getTotalDifficulty()) {
                    System.out.println("updating chaintip...");
                    setChainTip(blockHeader);
                    setCurrentDifficultyTotal(blockHeader.getTotalDifficulty());
                    setCurrentHash(hash);
                    setCurrentIndex(blockHeader.getIndex());
                    System.out.println("new chaintip set");

                    TreeSet<Transaction> txs = getCohorts();
                    BlockLSH.getCohortID(txs, BlockLSH.myCohortHash);
                }
                System.out.println("chaintip checked..");
                System.out.println("checking fileheader data");

                // Updates fileheader info in indexDB (if relevant)
                if (read(getIndexDB(), ("fblocks" + fileNum).getBytes()) == null) {
                    indexFile fileHeader = new indexFile(1, block.getIndex(), block.getIndex(), block.getTotalDifficulty(), block.getTotalDifficulty(), block.getDate(), block.getDate());
                    byte[] bytesFile = SerializationUtils.serialize(fileHeader);
                    add(getIndexDB(), ("fblocks" + fileNum).getBytes(), bytesFile);
                }
                else {
                    byte[] res = read(getIndexDB(), ("fblocks" + fileNum).getBytes());
                    indexFile oldIndex = (indexFile) SerializationUtils.deserialize(res);

                    long lowIndex = oldIndex.getLowIndex();
                    long highIndex = oldIndex.getHighIndex();
                    long lowWork = oldIndex.getLowWork();
                    long highWork = oldIndex.getHighWork();
                    String earlyDate = oldIndex.getEarlyDate();
                    String lateDate = oldIndex.getLateDate();

                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
                    Date oldEarly = sdf.parse(earlyDate);
                    Date oldLate = sdf.parse(lateDate);

                    Date newDate = sdf.parse(block.getDate());

                    // check if we need to update index value
                    if (lowIndex > block.getIndex()) {
                        lowIndex = block.getIndex();
                    } else if (highIndex < block.getIndex()) {
                        highIndex = block.getIndex();
                    }

                    // check if we need to update the TotalDifficulty value
                    if (lowWork > block.getTotalDifficulty()) {
                        lowWork = block.getTotalDifficulty();
                    } else if (highWork < block.getTotalDifficulty()) {
                        highWork = block.getTotalDifficulty();
                    }

                    // check if we need to update the date value
                    if (oldEarly.after(newDate)) {
                        earlyDate = block.getDate();
                    } else if (oldLate.before(newDate)) {
                        lateDate = block.getDate();
                    }

                    indexFile newIndex = new indexFile(oldIndex.getNumBlocks() + 1, lowIndex, highIndex, lowWork, highWork, earlyDate, lateDate);
                    byte[] bytesIndex = SerializationUtils.serialize(newIndex);
                    add(getIndexDB(), ("fblocks" + fileNum).getBytes(), bytesIndex);
                }
            }

            else {
                System.out.println("block metadata already stored..");
            }

            System.out.println("finished storing block..");

        } catch(Exception e) {
            System.out.println("STORE BLOCK ERR: " + e);
        } finally {
            finish(getBlockDB());
            finish(getIndexDB());
        }

    }

    /** Add block to LevelDB if it's one of the blocks requested
     * @param blocks The LinkedList of blocks we want to add
     * @return Returns whether the syncing was fully complete (true), or, whether some requested blocks were not received (false)
     */
    public boolean syncBlocks(LinkedList<Block> blocks) {
        System.out.println("Syncing blocks..");

        Miner mine = new Miner();

        for (Block b : blocks) {
            String h = mine.hash(b);
            System.out.println("block hash: " + h);
            if(getRequest().contains(h)) {
                // check if merkle tree is valid..
                String calcMerkle = Main.miner.genMerkleRoot(b.getTransactions());

                if(calcMerkle.equals(b.getMerkleRoot())) {
                    System.out.println("we requested this block so start storing it..");
                    storeBlock(h, b);
                    System.out.println("finished storing it..");
                    getRequest().remove(h);
                    System.out.println("removing it from request list");
                }
                else {
                    System.out.println("merkle root not correct..");
                    // no need to reject whole chain as if some blocks were valid, they can be stored in DB
                }
            }
            else {
                System.out.println("not in requested list..");
            }
        }

        // If some blocks were not sent by the user, request them again
        if(!getRequest().isEmpty()) {
            System.out.println("not all blocks received, so return false..");
            return false;
        }
        else {
            System.out.println("all fine and finished, return true..");
            return true;
        }
    }

    /** Check if the list of block headers is valid
     * @param headerList The LinkedList of block headers we want to validate
     * @return Returns whether the validation was successful or not..
     */
    public boolean validateHeaders(LinkedList<indexBlock> headerList) {
        System.out.println("validating headers..");
        boolean valid = false;

        Miner mine = new Miner();

        for (indexBlock header : headerList) {

            int index = headerList.indexOf(header);
            if(index != 0) {
                index = index - 1;
            }

            System.out.println("checking mined..");
            // Check if mined successfully
            if( !(mine.verifyMined(header)) ) {
                System.out.println("not mined");
                request.clear();
                System.out.println("request cleared..");
                return false;
            }

            System.out.println("checking linked..");
            // Check if chain is linked
            if( !(validateInChain(header, mine.hash(headerList.get(index)) )) ) {
                System.out.println("not linked..");
                request.clear();
                System.out.println("request cleared..");
                return false;
            }

            // Check if difficulty is correct
            int index2 = 0;
            if(index-1 > 0 ) {
                index2 = index-1;
            }

            if( !(validateDifficulty(header, headerList.get(index), headerList.get(index2))) ) {
                System.out.println("difficulty is not correct..");
                request.clear();
                System.out.println("request cleared..");
                return false;
            }

            System.out.println("adding new request: " + mine.hash(header) );
            request.add(0, mine.hash(header));

        }
        System.out.println("header validation finished...");
        valid = true;

        return valid;
    }

    /** Check if prevHash value of block is in users' LevelDB
     * @param block The block object we want to validate
     * @return Returns whether the validation is successful (true) or not (false)
     */
    public boolean validateInChain(Block block) {
        String pHash = block.getPreviousHash();

        Block pBlock = getBlock(pHash);

        if (pHash.equals("GENESIS") && block.getIndex() == 1) {
            return true;
        }

        else if(pBlock != null) {
            return true;
        }

        else {
            return false;
        }
    }

    /** Check if prevHash value of block is in users LevelDB
     * @param header The header object of the block we want to validate
     * @param inChain The hash of the prevHash value the header should have
     * @return Returns whether the validation was successful (true) or not (false)
     */
    public boolean validateInChain(indexBlock header, String inChain) {
        System.out.println("validating if in chain..");
        String pHash = header.getPrevHash();

        Block pBlock = getBlock(pHash);

        if (pHash.equals("GENESIS") && header.getIndex() == 1) {
            System.out.println("ph is genesis or index is 1");
            return true;
        }

        else if(pBlock != null || pHash.equals(inChain)) {
            System.out.println("no block found or ph equals hash given");
            return true;
        }

        else {
            System.out.println("none, so return false..");
            return false;
        }
    }

    /** Check that the difficulty levels in block are valid, ie. totalDifficulty count is correct, and individual difficultyLevel matches current consensus level
     * @param b The block object we want to validate
     * @return Returns whether the validation was successful (true) or not (false)
     */
    public boolean validateDifficulty(Block b) {
        boolean valid = false;

        try {
            // We need at least 3 blocks to calculate the difference in block creation between the child and the grandchild of the block
            if( !b.getPreviousHash().equals("GENESIS") && (b.getIndex() >= 3) ) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

                // Get the child block and its date
                byte[] res = read(getIndexDB(), ("b"+b.getPreviousHash()).getBytes());
                indexBlock prevBlock = (indexBlock) SerializationUtils.deserialize(res);
                Date parent = sdf.parse(prevBlock.getDate());

                // Get the grandchild block and its date
                byte[] res2 = read(getIndexDB(), ("b"+prevBlock.getPrevHash()).getBytes());
                indexBlock prevBlock2 = (indexBlock) SerializationUtils.deserialize(res2);
                Date child = sdf.parse(prevBlock2.getDate());

                // Calculate the difference in block creation between blocks to the nearest second
                long diffSeconds = (parent.getTime() - child.getTime()) / 1000 ;
                System.out.println("difference in seconds between blocks: " + diffSeconds);

                // Difficulty needs to be increased..
                if (diffSeconds <= blockInterval) {
                    long val = prevBlock.getDifficultyLevel() * 2;
                    System.out.println("dif should be: " + val);

                    // Check whether the value matches
                    if(b.getDifficultyLevel() == val) {
                        System.out.println("difficulty is correct");
                        if(b.getTotalDifficulty() == (prevBlock.getTotalDifficulty() + b.getDifficultyLevel())) {
                            System.out.println("total dif is right..");
                            return true;
                        }
                        else {
                            System.out.println("total not right..");
                            return false;
                        }
                    }
                    else {
                        System.out.println("dif not right..");
                        return false;
                    }
                }

                // Difficulty needs to be lowered..
                else if (diffSeconds > blockInterval) {
                    long val = prevBlock.getDifficultyLevel() / 2;
                    System.out.println("dif should be: " + val);

                    // Minimum difficulty should be 1
                    if(val < 1) {
                        val = 1;
                    }

                    // Check whether the value matches
                    if(b.getDifficultyLevel() == val) {
                        System.out.println("difficulty is correct");
                        if(b.getTotalDifficulty() == (prevBlock.getTotalDifficulty() + b.getDifficultyLevel())) {
                            System.out.println("total dif is right..");
                            return true;
                        }
                        else {
                            System.out.println("total not right..");
                            return false;
                        }
                    }
                    else {
                        System.out.println("dif not right..");
                        return false;
                    }
                }
            }

            // Difficulty at the first few blocks should be at 1, so check if this is the case
            else {
                if(b.getDifficultyLevel() == 1) {
                    System.out.println("dif is 1..");
                    return true;
                }
                else {
                    System.out.println("dif is not 1, so false..");
                    return false;
                }
            }
        }
        catch(Exception e) {
            System.out.println("get difficulty err: " + e);
        }

        return valid;
    }

    /** Check that the difficulty levels in block headers are valid, ie. totalDifficulty count is correct, and individual difficultyLevel matches current consensus level
     * @param h The header we are doing validation on
     * @param h2 The child header
     * @param h3 the grandchild header
     * @return Returns whether the validation was successful (true) or not (false)
     */
    public boolean validateDifficulty(indexBlock h, indexBlock h2, indexBlock h3) {
        boolean valid = false;

        try {
            if( !h.getPrevHash().equals("GENESIS") && (h.getIndex() >= 3) ) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

                // Get the date of the child header
                Date parent = sdf.parse(h2.getDate());
                // Get the date of the grandchild header
                Date child = sdf.parse(h3.getDate());

                // Calculate difference between their creation to the nearest second
                long diffSeconds = (parent.getTime() - child.getTime()) / 1000 ;
                System.out.println("difference in seconds between blocks: " + diffSeconds);

                // Difficulty should be increased..
                if (diffSeconds <= blockInterval) {
                    System.out.println("difficulty is increased..");
                    long val = h2.getDifficultyLevel() * 2;
                    System.out.println("dif should be: " + val);

                    if(h.getDifficultyLevel() == val) {
                        System.out.println("difficulty is correct");
                        if(h.getTotalDifficulty() == (h2.getTotalDifficulty() + h.getDifficultyLevel())) {
                            System.out.println("total dif is right..");
                            return true;
                        }
                        else {
                            System.out.println("total not right..");
                            return false;
                        }
                    }
                    else {
                        System.out.println("dif not right..");
                        return false;
                    }
                }

                // Difficulty should be lowered..
                else if (diffSeconds > blockInterval) {
                    System.out.println("difficulty is decreased..");
                    long val = h2.getDifficultyLevel() / 2;

                    // Minimum difficulty should be 1
                    if(val < 1) {
                        val = 1;
                    }

                    System.out.println("dif should be: " + val);

                    if(h.getDifficultyLevel() == val) {
                        System.out.println("difficulty is correct");
                        if(h.getTotalDifficulty() == (h2.getTotalDifficulty() + h.getDifficultyLevel())) {
                            System.out.println("total dif is right..");
                            return true;
                        }
                        else {
                            System.out.println("total not right..");
                            return false;
                        }
                    }
                    else {
                        System.out.println("dif not right..");
                        return false;
                    }
                }
            }

            // Difficulty at the first few blocks should be at 1, so check if this is the case
            else {
                if(h.getDifficultyLevel() == 1) {
                    System.out.println("dif is 1..");
                    return true;
                }
                else {
                    System.out.println("dif is not 1, so false..");
                    return false;
                }
            }
        }
        catch(Exception e) {
            System.out.println("get difficulty err: " + e);
        }

        return valid;
    }

    /** Calculate the difficulty level the next block should be at
     * @return The difficulty level the next block should be at
     */
    public long getDifficulty() {
        long val = 1;
        try {
            if(chainTip != null) {
                if(!chainTip.getPrevHash().equals("GENESIS")) {

                    // Get chaintip date
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
                    Date parent = sdf.parse(chainTip.getDate());

                    // Get chaintip header
                    byte[] res = read(getIndexDB(), ("b"+chainTip.getPrevHash()).getBytes());
                    indexBlock prevBlock = (indexBlock) SerializationUtils.deserialize(res);

                    // Get chaintip child date
                    Date child = sdf.parse(prevBlock.getDate());

                    // Calculate difference in block creation to the nearest second
                    long diffSeconds = (parent.getTime() - child.getTime()) / 1000;
                    System.out.println("difference in seconds between blocks: " + diffSeconds);

                    // Difficulty should be increased..
                    if (diffSeconds < blockInterval) {
                        System.out.println("difficulty is increased..");
                        val = chainTip.getDifficultyLevel() * 2;
                    }
                    // Difficulty should be lowered..
                    else if (diffSeconds > blockInterval) {
                        System.out.println("difficulty is decreased..");
                        val = chainTip.getDifficultyLevel() / 2;
                    }
                }
            }
        }
        catch(Exception e) {
            System.out.println("get difficulty err: " + e);
        }

        // min difficulty should be 1
        if(val < 1) {
            val = 1;
        }

        return val;
    }

    /** Store the block we will validate later
     * @param b The block we want to store
     * @param pa The address of the peer we received this block from
     */
    public void addUnofficial(Block b, PeerAddress pa) {
        unofficial uo = new unofficial(b, pa);
        unofficial.addFirst(uo);
    }

    /** Validate and store the unofficial blocks we received from the peer
     * @param pa The address of the peer
     */
    public void checkUnofficial(PeerAddress pa) {
        try {
            // loop over all blocks
            for (unofficial ub : unofficial) {
                // check if we received the block from this peer
                if (ub.getPeer().equals(pa)) {
                    // check if block is in chain
                    if (validateInChain(ub.getBlock())) {
                        // check if the difficulty levels of the block are valid
                        if(validateDifficulty(ub.getBlock())) {
                            boolean valid = true;
                            // check if all tx have been mined
                            for(Transaction t : ub.getBlock().getTransactions()) {
                                if(!new Miner().verifyMined(t)){
                                    valid = false;
                                    break;
                                }
                            }
                            if(valid) {
                                // all valid, so store and remove from list
                                storeBlock(new Miner().hash(ub.getBlock()), ub.getBlock());
                                unofficial.remove(ub);
                            }
                        }
                        else {
                            throw new Exception();
                        }
                    } else {
                        throw new Exception();
                    }
                }
            }
        }
        catch(Exception e) {
            System.out.println("Unofficial blocks not yet synced");
        }
    }

    /** Query current chaintip of the user and go back the chain for 30 blocks and return that blocks hash
     *  see ->     https://stackoverflow.com/questions/49065176/how-many-confirmations-should-i-have-on-ethereum
     *  @return Returns the hash of the latest confirmed block
     */
    public String getConfirmed() {
        int count = 1;
        String hash = "GENESIS";
        if(getChainTip() != null) {
            hash = getChainTip().getPrevHash();
        }

        // TODO; needs more testing, should probably request whole chain just in case
        while(count != 30) {
            Block b = getBlock(hash);

            // Reached end of chain, ie. user doesn't have 30 blocks in its DB so we just go as far as possible
            if(b == null) {
                return hash;
            }

            else {
                hash = b.getPreviousHash();
                count++;
            }
        }

        return hash;
    }

    /** Get the blocks another peer has requested from us
     * @param hashes The ArrayList of String hashes the user requests
     * @return Returns the full blocks inside a LinkedList
     */
    public LinkedList<Block> getRequestedBlocks(ArrayList<String> hashes) {
        System.out.println("\n\ngetting requested blocks..");
        System.out.println("blocks wanted: " + hashes);

        LinkedList<Block> blockList = new LinkedList<>();

        for (String h : hashes) {
            System.out.println("we need hash: " + h);
            byte[] contents = read(getBlockDB(), h.getBytes() );
            Block b = (Block) SerializationUtils.deserialize(contents);
            System.out.println("got block with ph: " + b.getPreviousHash());
            blockList.addLast(b); // order won't matter later
            System.out.println("block added: " + new Miner().hash(b));
        }

        System.out.println("finished adding blocks, now returning..");
        return blockList;
    }

    /** Get the headers of the blocks another peer has requested from us
     * @param h The hash from which the other peer wants to build upon
     * @return Returns the LinkedList of headers the peer has requested
     */
    public LinkedList<indexBlock> getHeaders(String h) {
        System.out.println("Getting chain headers..");
        LinkedList<indexBlock> headerList = new LinkedList<>();

        // add chainTip
        indexBlock header = getChainTip();
        headerList.addFirst(header);
        System.out.println("added chaintip header..");

        // check if we need to go further down the chain
        while( !(header.getPrevHash().equals(h)) || (header.getPrevHash().equals("GENESIS")) ) {
            String prevHash = header.getPrevHash();
            byte[] contents = read(getIndexDB(), ("b"+prevHash).getBytes() );
            header = (indexBlock) SerializationUtils.deserialize(contents);
            headerList.addFirst(header);
            System.out.println("added: " + header.toString());
        }
        System.out.println("finished getting headers..");

        return headerList;
    }

    /** Retrieves block from block DB
     * @param keyHash The hash of the block we want to read as a String
     * @return Returns the block object we have requested (or null if such object doesn't exist)
     */
    public Block getBlock(String keyHash) {
        try {
            byte[] contents = read(getBlockDB(), keyHash.getBytes());
            Block b = (Block) SerializationUtils.deserialize(contents);
            return b;
        } catch(Exception e) {
            System.out.println("GET BLOCK ERR: " + e);
            return null;
        } finally {
            finish(getBlockDB());
        }
    }

    /** Create/Open LevelDB that stores block
     * @return Returns the blockDB database
     */
    public DB getBlockDB() {
        try {
            options.createIfMissing(true);
            blockDB = factory.open(new File(("blocks" + fileNum)), options);
            return blockDB;
        } catch(IOException ioe) {
            System.out.println("GET BLOCK_DB IOE ERROR: " + ioe);
            return null;
        }
    }

    /** Create/Open LevelDB that stores block headers
     * @return Returns the indexDB database
     */
    public DB getIndexDB() {
        try {
            options.createIfMissing(true);
            indexDB = factory.open(new File("index"), options);
            return indexDB;
        } catch(IOException ioe) {
            System.out.println("GET INDEX_DB IOE ERROR: " + ioe);
            return null;
        }
    }

    /** Gets the current index number of the file
     * @return Returns the index of the file */
    public int getFileNum() {
        return fileNum;
    }

    /** Returns chain tip
     * @return Returns the chaintip*/
    public indexBlock getChainTip() {
        return chainTip;
    }

    /** Update chain tip */
    public void setChainTip(indexBlock newTip) {
        this.chainTip = newTip;
    }

    /** Gets current index of the chaintip */
    public long getCurrentIndex() {
        return currentIndex;
    }

    /** Sets the current index of the chaintip */
    public void setCurrentIndex(long currentIndex) {
        this.currentIndex = currentIndex;
    }

    /** Gets the hash of the chaintip */
    public String getCurrentHash() {
        return currentHash;
    }

    /** Sets the hash of the chaintip */
    public void setCurrentHash(String currentHash) {
        this.currentHash = currentHash;
    }

    /** Gets the current difficulty total of the chaintip */
    public long getCurrentDifficultyTotal() {
        return currentDifficultyTotal;
    }

    /** Sets the current difficulty level of the chaintip */
    public void setCurrentDifficultyTotal(long currentDifficultyTotal) { this.currentDifficultyTotal = currentDifficultyTotal; }

    /** Returns the request blocks inside an ArrayList of Strings */
    public ArrayList<String> getRequest() {
        return request;
    }
}

/** The header of the file object */
class indexFile implements Serializable {
    public int numBlocks;
    public long lowIndex;
    public long highIndex;
    public long lowWork;
    public long highWork;
    public String earlyDate;
    public String lateDate;

    public indexFile(int numBlocks, long lowIndex, long highIndex, long lowWork, long highWork, String earlyDate, String lateDate) {
        this.numBlocks = numBlocks;
        this.lowIndex = lowIndex;
        this.highIndex = highIndex;
        this.lowWork = lowWork;
        this.highWork = highWork;
        this.earlyDate = earlyDate;
        this.lateDate = lateDate;
    }

    public int getNumBlocks() {
        return numBlocks;
    }

    public long getLowIndex() {
        return lowIndex;
    }

    public long getHighIndex() {
        return highIndex;
    }

    public long getLowWork() {
        return lowWork;
    }

    public long getHighWork() {
        return highWork;
    }

    public String getEarlyDate() {
        return earlyDate;
    }

    public String getLateDate() {
        return lateDate;
    }

    @Override
    public String toString() {
        return "indexFile{" +
                "numBlocks=" + numBlocks +
                ", lowIndex=" + lowIndex +
                ", highIndex=" + highIndex +
                ", lowWork=" + lowWork +
                ", highWork=" + highWork +
                ", earlyDate='" + earlyDate + '\'' +
                ", lateDate='" + lateDate + '\'' +
                '}';
    }
}

/** The header of the block */
class indexBlock implements Serializable {
    public long index;
    public long difficultyLevel;
    public int fileNumber;
    public String date;
    public String merkleRoot;

    public String previousHash;
    public long nonce;
    public long totalDifficulty;

    public indexBlock(long index, long difficultyLevel, long totalWork, int fileNumber, String date, String prevHash, String mr, long n) {
        this.index = index;
        this.difficultyLevel = difficultyLevel;
        this.totalDifficulty = totalWork;
        this.fileNumber = fileNumber;
        this.date = date;
        this.previousHash = prevHash;
        this.merkleRoot = mr;
        this.nonce = n;
    }

    public long getIndex() {
        return index;
    }

    public long getDifficultyLevel() {
        return difficultyLevel;
    }

    public long getTotalDifficulty() {
        return totalDifficulty;
    }

    public int getFileNumber() {
        return fileNumber;
    }

    public String getDate() {
        return date;
    }

    public String getPrevHash() {
        return previousHash;
    }

    public String getMerkleRoot() { return merkleRoot; }

    public long getNonce() { return nonce; }

    @Override
    public String toString() {
        return "Block{" +
                "index=" + index +
                ", date='" + date + '\'' +
                ", previousHash='" + previousHash + '\'' +
                ", nonce=" + nonce +
                ", difficultyLevel=" + difficultyLevel +
                ", merkleRoot='" + merkleRoot + '\'' +
                ", totalDifficulty=" + totalDifficulty +
                '}';
    }
}

/** The blocks and from whom they were received from */
class unofficial {
    private final Block block;
    private final PeerAddress peer;

    public unofficial(Block b, PeerAddress pa) {
        this.block = b;
        this.peer = pa;
    }

    public Block getBlock() {
        return block;
    }

    public PeerAddress getPeer() {
        return peer;
    }
}
