package sigma.model.filter.operator;

import sigma.model.filter.SqlPredicate;

/**
 * Regex operator: regex
 * In PostgreSQL, this is converted to a LIKE pattern
 */
public class RegexOperator extends ComparisonOperator {

    public RegexOperator() {
        super("regex");
    }

    @Override
    public SqlPredicate apply(String fieldName, Object value) {
        return SqlPredicate.jsonbRegex(fieldName, value);
    }
}
