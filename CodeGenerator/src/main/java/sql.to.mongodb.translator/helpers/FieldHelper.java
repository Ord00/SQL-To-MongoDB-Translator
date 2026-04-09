package sql.to.mongodb.translator.helpers;

import sql.to.mongodb.translator.ir.Field;

public class FieldHelper {

    public static String getFullFieldName(Field field) {
        if (field == null) return "";
        if (field.getSource() != null && !field.getSource().isEmpty()) {
            return field.getSource() + "." + field.getField();
        }
        return field.getField();
    }
}