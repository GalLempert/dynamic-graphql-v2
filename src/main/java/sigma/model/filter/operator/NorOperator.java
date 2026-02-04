package sigma.model.filter.operator;

import sigma.model.filter.SqlPredicate;

import java.util.List;

/**
 * Logical NOR operator: $nor
 * NOR is equivalent to NOT(OR(...))
 */
public class NorOperator extends LogicalOperator {

    public NorOperator() {
        super("$nor");
    }

    @Override
    public SqlPredicate applyPredicates(List<SqlPredicate> predicates) {
        if (predicates.isEmpty()) {
            return new SqlPredicate("1=1");
        }
        // NOR = NOT(OR(...))
        SqlPredicate orPredicate = SqlPredicate.or(predicates.toArray(new SqlPredicate[0]));
        return SqlPredicate.not(orPredicate);
    }
}
