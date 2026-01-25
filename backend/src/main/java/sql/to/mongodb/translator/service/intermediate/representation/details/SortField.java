package sql.to.mongodb.translator.service.intermediate.representation.details;

import lombok.Getter;
import lombok.Setter;

@Getter
public class SortField {
    @Setter
    private String field;
    private SortDirection direction = SortDirection.ASC;
    @Setter
    private String source;

    @Getter
    public enum SortDirection {
        ASC(1),
        DESC(-1);

        private final int mongoValue;

        SortDirection(int mongoValue) {
            this.mongoValue = mongoValue;
        }

        public static SortDirection fromString(String dir) {
            if (dir == null || dir.equalsIgnoreCase("ASC")) {
                return ASC;
            } else if (dir.equalsIgnoreCase("DESC")) {
                return DESC;
            }
            throw new IllegalArgumentException("Invalid sort direction: " + dir);
        }
    }

    public SortField() {}

    public SortField(String field, SortDirection direction) {
        this.field = field;
        this.direction = direction;
    }

    public SortField(String field, String direction) {
        this.field = field;
        this.direction = SortDirection.fromString(direction);
    }

    public void setDirection(SortDirection direction) { this.direction = direction; }
    public void setDirection(String direction) {
        this.direction = SortDirection.fromString(direction);
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
