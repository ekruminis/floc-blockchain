import org.apache.commons.lang3.SerializationUtils;

import java.io.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


public class Main extends Thread {
    // Static objects so they remain the same through all classes
    static Network network = new Network();
    static Miner miner = new Miner();
    static Encryption encryption = new Encryption();

    private volatile boolean active = true;
    String cmd = "";

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    // command line UI
    public void run () {
        System.out.println(" *** VALID COMMANDS ***");
        System.out.println("'mine'          -> mines a block and sends it off to other peers");
        System.out.println("'my-cohort'     -> calculates cohort ID for user");
        System.out.println("'all-cohorts'   -> prints all currently valid cohorts in network");
        System.out.println("'test'          -> generates and mines 10 blocks with 1000 random cohorts");
        System.out.println("'settings'      -> change settings of model (NOT SAVED AFTER EXIT)");
        System.out.println("'exit'          -> shuts down");

        while(isActive()) {
            System.out.println("Enter a command...");
            Scanner sc = new Scanner(System.in);

            cmd = sc.nextLine();

            if(cmd.equals("mine")) {
                for(int x=0;x<100;x++) {
                    System.out.println("mining a new block..");

                    miner.fetchTransactions(network.getTxFile(), network);
                    Block unminedBlock = miner.createBlock(network.getChain());
                    Block minedBlock = miner.mineBlock(unminedBlock);

                    try {
                        new PrintWriter(network.getTxFile()).close();
                        miner.txs = new TreeSet<Transaction>();
                    } catch (Exception er) {
                        System.out.println("FAIL DELETING TX FILE CONTENTS: " + er);
                    }

                    System.out.println("txs in block: " + minedBlock.getTransactions().size());
                    network.getChain().storeBlock(miner.hash(minedBlock), minedBlock);
                    network.announce(minedBlock);
                }
            }
            else if(cmd.equals("my-cohort")) {
//                System.out.println("getting cohort ID..");
//                String s = BlockLSH.getCohortID(network.getChain().getCohorts(), BlockLSH.getCohortHash());
//
//                System.out.println("my full cohort =    " + BlockLSH.getCohortHash());
//                System.out.println("my cohort ID =      " + s);

                System.out.println("my cohort ID =      " + BlockLSH.readCohortID());
            }
            else if(cmd.equals("all-cohorts")) {
                System.out.println("getting all valid cohorts..");
                TreeSet<Transaction> txs = network.getChain().getCohorts();

                for(Transaction t : txs) {
                    System.out.println(t.getCohort());
                }

                System.out.println("\n** all cohorts printed **");
            }
            else if(cmd.equals("exit")) {
                System.out.println("exiting application");
                setActive(false);
                System.exit(0);
            }
            else if(cmd.equals("test")) {
                for(int x2=0;x2<10;x2++) {
                    TreeSet<Transaction> txs = new TreeSet<>();
                    for (int y = 0; y < 1000; y++) {
                    
                        // generate a random cohort hash of 50 bits
                        String h = "";
                        for (int x = 0; x < 50; x++) {
                            Random rand = new Random();
                            int r = rand.nextInt() % 2;
                            h += (int) Math.round(Math.random());
                        }
                        Transaction tx = new Transaction(h);
                        BlockLSH.sendTransaction(network, tx);
                    }

                    System.out.println("mining a new block..");

                    miner.fetchTransactions(network.getTxFile(), network);
                    Block unminedBlock = miner.createBlock(network.getChain());
                    Block minedBlock = miner.mineBlock(unminedBlock);

                    try {
                        new PrintWriter(network.getTxFile()).close();
                        miner.txs = new TreeSet<Transaction>();
                    } catch (Exception er) {
                        System.out.println("FAIL DELETING TX FILE CONTENTS: " + er);
                    }

                    System.out.println("txs in block: " + minedBlock.getTransactions().size());
                    network.getChain().storeBlock(miner.hash(minedBlock), minedBlock);
                    network.announce(minedBlock);
                }
            }

            else if(cmd.equals("settings")) {
                System.out.print("\n(current=" + BlockLSH.kSize + ") Min. number of users for a valid cohort ID: ");
                BlockLSH.kSize = Integer.parseInt(sc.nextLine());

                System.out.print("\n(current=" + Miner.txDif + ") Min. difficulty level of valid transaction object: ");
                Miner.txDif = Integer.parseInt(sc.nextLine());

                System.out.print("\n(current=" + Blockchain.expiredTime + ") Number of seconds before a mined cohort hash expires: ");
                Blockchain.expiredTime = Long.parseLong(sc.nextLine());

                System.out.print("\ncurrent=" + Blockchain.blockInterval +") Number of seconds between each new block: ");
                Blockchain.blockInterval = Long.parseLong(sc.nextLine());

                System.out.print("\ncurrent=" + BlockLSH.hashSize + ") Number of bits of the cohort hash to send: ");
                Blockchain.blockInterval = Long.parseLong(sc.nextLine());

                System.out.println("Settings updated.. (NOT SAVED AFTER APP CLOSURE)");
            }
            else {
                System.out.println("wrong input, try the following;");
                System.out.println("'mine'          -> mines a block and sends it off to other peers");
                System.out.println("'my-cohort'     -> calculates cohort ID for user");
                System.out.println("'all-cohorts'   -> prints all currently valid cohorts in network");
                System.out.println("'test'          -> generates 1000 random cohorts, mines block");
                System.out.println("'settings'      -> change settings of model (NOT SAVED AFTER EXIT)");
                System.out.println("'exit'          -> shuts down");
            }
        }
    }

    public static void main(String[] args) {
        System.out.println("opened..");

        Main m = new Main();
        m.start();

        Runnable expiredRunnable = new Runnable() {
            public void run() {
                System.out.println("cohort expired, sending new..");
                BlockLSH.sendTransaction(network);
            }
        };

        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);

        System.out.println("connecting..");
        network.connect(args[0], Integer.parseInt(args[1]), Integer.parseInt(args[2]));
        //network.connect("86.40.24.244", 1233, 1234);

        long initDelay = 0;
	
	// read (if exists) my cohort data to see when it needs to be updated
        if(new File("myCohortObj").exists()) {
            try {
                // Open file
                FileInputStream fis = new FileInputStream("myCohortObj");
                ObjectInputStream ois = new ObjectInputStream(fis);
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

                Date currentDate = null;

                try {
                    currentDate = sdf.parse(Transaction.makeDate());
                } catch (ParseException e) {
                    e.printStackTrace();
                }

                // add tx, ensuring no duplicates
                Object obj = null;
                while((obj = ois.readObject()) != null) {
                    Transaction tx = (Transaction)obj;
                    Date msgDate = sdf.parse(tx.getDate());
                    long diffTime = (currentDate.getTime() - msgDate.getTime()) / 1000;

                    if (diffTime < Blockchain.expiredTime) {
                        initDelay = Blockchain.expiredTime - diffTime;

                        BlockLSH.myCohortHash = tx.getCohort();
                        BlockLSH.readCohortID();
                        System.out.println("insdie");
                    }
                }

                fis.close();
            } catch(Exception e) {
                System.out.println("FETCHING ERROR: " + e);
            }
        }

        executor.scheduleAtFixedRate(expiredRunnable, initDelay, Blockchain.expiredTime, TimeUnit.SECONDS);
	
	// on client closure, save currently up-to-date chain metadata
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            public void run() {
                System.out.println("updating chaintip data..");
                if(network.getChain().getChainTip() != null) {
                    network.getChain().add(network.getChain().getIndexDB(), "chaintip".getBytes(), SerializationUtils.serialize(network.getChain().getChainTip()));
                    System.out.println("chaintip data updated..");
                }
            }
        }));

    }
}
