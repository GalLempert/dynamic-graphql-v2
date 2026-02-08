package sigma.model.filter.operator;

import sigma.model.filter.SqlPredicate;

import java.util.List;

/**
 * Logical NOT operator: not
 * Note: not works differently - it negates a single expression
 */
public class NotOperator extends LogicalOperator {

    public NotOperator() {
        super("not");
    }

    @Override
    public SqlPredicate applyPredicates(List<SqlPredicate> predicates) {
        if (predicates.isEmpty()) {
            return new SqlPredicate("1=1");
        }
        if (predicates.size() > 1) {
            throw new IllegalArgumentException("not operator only accepts a single condition");
        }
        return SqlPredicate.not(predicates.get(0));
    }
}
