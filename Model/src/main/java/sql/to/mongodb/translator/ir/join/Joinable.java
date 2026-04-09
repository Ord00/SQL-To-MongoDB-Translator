package sql.to.mongodb.translator.ir.join;

public interface Joinable {
    String getAlias();
    void setAlias(String alias);
}
