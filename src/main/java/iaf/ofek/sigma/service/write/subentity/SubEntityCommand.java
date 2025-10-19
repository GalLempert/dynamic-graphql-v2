package iaf.ofek.sigma.service.write.subentity;

/**
 * Command representing a mutation to a sub-entity collection.
 */
interface SubEntityCommand {

    void apply(SubEntityCollection collection);
}
