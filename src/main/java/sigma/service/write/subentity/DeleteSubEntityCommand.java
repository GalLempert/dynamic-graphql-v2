package sigma.service.write.subentity;

/**
 * Command that marks an existing sub-entity entry as logically deleted.
 */
class DeleteSubEntityCommand implements SubEntityCommand {

    private final Long id;

    DeleteSubEntityCommand(Long id) {
        this.id = id;
    }

    @Override
    public void apply(SubEntityCollection collection) {
        collection.delete(id);
    }
}
