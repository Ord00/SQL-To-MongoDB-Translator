package sql.to.mongodb.translator.ir;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
// можно улучшить, добавив возможность арифметических операций или подзапросов
public class SortField extends Field {

    private SortDirection direction = SortDirection.ASC;

    @Getter
    public enum SortDirection {
        ASC(1),
        DESC(-1);

        private final int mongoValue;

        SortDirection(int mongoValue) {
            this.mongoValue = mongoValue;
        }
    }

    public SortField(String source, String field, SortDirection direction) {
        this.source = source;
        this.field = field;
        this.direction = direction;
    }

    public SortField(String source, String field) {
        super(source, field);
    }

    public SortField(String source) {
        this.source = source;
    }

    public void setDirection(boolean isAsc) {
        this.direction = isAsc ? SortDirection.ASC : SortDirection.DESC;
    }

    public String getFullField() {
        if (source != null && !source.isEmpty()) {
            return source + "." + field;
        }
        return field;
    }

    @Override
    public String toString() {
        return String.format("%s %s", getFullField(), direction);
    }
}
