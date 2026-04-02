package sql.to.mongodb.translator.service.code.generator.helpers;

import sql.to.mongodb.translator.service.intermediate.representation.model.Field;

public class FieldHelper {

    public static String getFullFieldName(Field field) {
        if (field == null) return "";
        if (field.getSource() != null && !field.getSource().isEmpty()) {
            return field.getSource() + "." + field.getField();
        }
        return field.getField();
    }
}