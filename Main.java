import org.apache.commons.lang3.SerializationUtils;

import java.io.PrintWriter;
import java.util.Random;
import java.util.Scanner;
import java.util.TreeSet;


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
        System.out.println("'send'          -> calculates cohort and sends it off to other peers");
        System.out.println("'mine'          -> mines a block and sends it off to other peers");
        System.out.println("'my-cohort'     -> calculates cohort ID for user");
        System.out.println("'all-cohorts'   -> prints all currently valid cohorts in network");
        System.out.println("'test'          -> generates 1000 random cohorts, mines block");
        System.out.println("'settings'      -> change settings of model (NOT SAVED AFTER EXIT)");
        System.out.println("'exit'          -> shuts down");

        while(isActive()) {
            System.out.println("Enter a command...");
            Scanner sc = new Scanner(System.in);

            cmd = sc.nextLine();
            if(cmd.equals("send")) {
                System.out.println("sending my cohort ID..");

                BlockLSH.sendTransaction(network);
            }
            else if(cmd.equals("mine")) {
                System.out.println("mining a new block..");

                miner.fetchTransactions(network.getTxFile());
                Block unminedBlock = miner.createBlock(network.getChain());
                Block minedBlock = miner.mineBlock(unminedBlock);

                try {
                    new PrintWriter(network.getTxFile()).close();
                    miner.txs =  new TreeSet<Transaction>();
                } catch(Exception er) {
                    System.out.println("FAIL DELETING TX FILE CONTENTS: " + er);
                }

                System.out.println("txs in block: " + minedBlock.getTransactions().size());
                network.getChain().storeBlock(miner.hash(minedBlock), minedBlock);
                network.announce(minedBlock);
            }
            else if(cmd.equals("my-cohort")) {
                System.out.println("getting cohort ID..");
                String s = BlockLSH.getCohortID(network.getChain().getCohorts(), BlockLSH.getCohortHash());

                System.out.println("my full cohort =    " + BlockLSH.getCohortHash());
                System.out.println("my cohort ID =      " + s);
            }
            else if(cmd.equals("all-cohorts")) {
                System.out.println("getting all valid cohorts..");
                TreeSet<Transaction> txs = network.getChain().getCohorts();

                for(Transaction t : txs) {
                    System.out.println(t.getCohortID());
                }

                System.out.println("\n** all cohorts printed **");
            }
            else if(cmd.equals("exit")) {
                System.out.println("exiting application");
                setActive(false);
                System.exit(0);
            }
            else if(cmd.equals("test")) {
                for(int y=0;y<1000;y++) {
                    String h = "";
                    for (int x = 0; x < 50; x++) {
                        Random rand = new Random();
                        int r = rand.nextInt() % 2;
                        h += (int)Math.round( Math.random() );
                    }
                    Transaction tx = new Transaction(h);
                    BlockLSH.sendTransaction(network,tx);
                }

                System.out.println("mining a new block..");

                miner.fetchTransactions(network.getTxFile());
                Block unminedBlock = miner.createBlock(network.getChain());
                Block minedBlock = miner.mineBlock(unminedBlock);

                try {
                    new PrintWriter(network.getTxFile()).close();
                    miner.txs =  new TreeSet<Transaction>();
                } catch(Exception er) {
                    System.out.println("FAIL DELETING TX FILE CONTENTS: " + er);
                }

                System.out.println("txs in block: " + minedBlock.getTransactions().size());
                network.getChain().storeBlock(miner.hash(minedBlock), minedBlock);
                network.announce(minedBlock);

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

                System.out.println("Settings updated.. (NOT SAVED AFTER APP CLOSURE)");
            }
            else {
                System.out.println("wrong input, try the following;");
                System.out.println("'send'          -> calculates cohort and sends it off to other peers");
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

        System.out.println("connecting..");
        network.connect(args[0], Integer.parseInt(args[1]), Integer.parseInt(args[2]));

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
