package uk.ac.cf._5.group14.One_To_One.Notes;

public class StaleNoteException extends RuntimeException {
    public StaleNoteException() { super("The saved note changed while this draft was open"); }
}
