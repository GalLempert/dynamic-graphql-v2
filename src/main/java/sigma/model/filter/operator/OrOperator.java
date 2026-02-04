package sigma.model.filter.operator;

import sigma.model.filter.SqlPredicate;

import java.util.List;

/**
 * Logical OR operator: $or
 */
public class OrOperator extends LogicalOperator {

    public OrOperator() {
        super("$or");
    }

    @Override
    public SqlPredicate applyPredicates(List<SqlPredicate> predicates) {
        if (predicates.isEmpty()) {
            return new SqlPredicate("1=0");
        }
        return SqlPredicate.or(predicates.toArray(new SqlPredicate[0]));
    }
}
