package sigma.model.filter.operator;

import sigma.model.filter.SqlPredicate;

import java.util.List;

/**
 * Logical AND operator: $and
 */
public class AndOperator extends LogicalOperator {

    public AndOperator() {
        super("$and");
    }

    @Override
    public SqlPredicate applyPredicates(List<SqlPredicate> predicates) {
        if (predicates.isEmpty()) {
            return new SqlPredicate("1=1");
        }
        return SqlPredicate.and(predicates.toArray(new SqlPredicate[0]));
    }
}
