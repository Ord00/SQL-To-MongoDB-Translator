package sql.to.mongodb.translator.service.intermediate.representation;

import lombok.Getter;
import lombok.Setter;
import sql.to.mongodb.translator.service.intermediate.representation.details.ConditionNode;
import sql.to.mongodb.translator.service.intermediate.representation.details.JoinInfo;
import sql.to.mongodb.translator.service.intermediate.representation.details.ProjectionField;
import sql.to.mongodb.translator.service.intermediate.representation.details.SortField;
import sql.to.mongodb.translator.service.intermediate.representation.details.SubqueryInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
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
    private List<SubqueryInfo> subqueries = new ArrayList<>();
    private List<ConditionNode> whereConditions = new ArrayList<>();
    private List<ConditionNode> havingConditions = new ArrayList<>();
    private List<String> groupByFields = new ArrayList<>();
    private List<ProjectionField> projectionFields = new ArrayList<>();
    private Map<String, String> aliases = new HashMap<>();

    private Integer limit;
    private Integer offset;

    private List<SortField> orderBy = new ArrayList<>();
}
