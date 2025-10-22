package sigma.service.write.subentity;

/**
 * Command that marks an existing sub-entity entry as logically deleted.
 */
class DeleteSubEntityCommand implements SubEntityCommand {

    private final String myId;

    DeleteSubEntityCommand(String myId) {
        this.myId = myId;
    }

    @Override
    public void apply(SubEntityCollection collection) {
        collection.delete(myId);
    }
}
