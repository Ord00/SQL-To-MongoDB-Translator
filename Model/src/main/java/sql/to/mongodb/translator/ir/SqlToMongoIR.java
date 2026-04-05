package sql.to.mongodb.translator.ir;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import sql.to.mongodb.translator.ir.condition.ConditionNode;
import sql.to.mongodb.translator.ir.join.JoinInfo;
import sql.to.mongodb.translator.ir.projection.Projectionable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@EqualsAndHashCode
public class SqlToMongoIR {

    private boolean requiresAggregation = false;
    private boolean distinct = false;
    private boolean hasJoins = false;
    private boolean hasGroupBy = false;
    private boolean hasHaving = false;
    private boolean hasSubqueries = false;
    private boolean hasAggregateFunctions = false;
    private boolean hasWindowFunctions = false;
    private boolean hasComplexProjections = false;
    private boolean hasExistsConditions = false;
    private boolean hasCorrelatedSubqueries = false;

    private String mainCollection;
    private List<JoinInfo> joins = new ArrayList<>();
    private ConditionNode whereCondition;
    private ConditionNode havingCondition;
    private List<GroupByField> groupByFields = new ArrayList<>();
    private List<Projectionable> projectionFields = new ArrayList<>();
    private Map<String, String> aliases = new HashMap<>();

    private Integer limit;
    private Integer offset;

    private List<SortField> orderBy = new ArrayList<>();
}
