package sql.to.mongodb.translator.service.intermediate.representation.model;

import lombok.Getter;
import lombok.Setter;

@Getter
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

    public SortField() {}

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
