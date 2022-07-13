import com.sun.istack.internal.NotNull;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Transaction implements Serializable, Comparable<Transaction> {
    private String date;
    private String cohort;
    public long nonce;

    /** Returns current date
     * @return Returns the date as a String in the 'yyyy-MM-dd HH:mm:ss.SSS' format*/
    public static String makeDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        return sdf.format(new Date().getTime());
    }

    public Transaction(String c) {
        this.date = makeDate();
        this.cohort = c;
        this.nonce = 0;
    }

    @Override
    public String toString() {
        return "Transaction{" +
                "date='" + date + '\'' +
                ", cohort='" + cohort + '\'' +
                ", nonce=" + nonce +
                '}';
    }

    /** Returns cohort ID of the transaction
     * @return cohort ID value
     */
    public String getCohort() {
        return cohort;
    }

    /** Returns nonce value of the transaction
     * @return nonce value
     */
    public long getNonce() {
        return nonce;
    }

    /** Returns the date transaction was made on
     * @return date value
     */
    public String getDate() { return date; }

    public void setCohort(String cohort) {
        this.cohort = cohort;
    }

    @Override
    public boolean equals(Object obj) {
        return !super.equals(obj);
    }

    /**
     * Compares two transaction objects by their hash values
     * @param tx the object to compare to
     */
    @Override
    public int compareTo(@NotNull Transaction tx) {
        return BlockLSH.hash(this).compareTo(BlockLSH.hash(tx));
    }
}
