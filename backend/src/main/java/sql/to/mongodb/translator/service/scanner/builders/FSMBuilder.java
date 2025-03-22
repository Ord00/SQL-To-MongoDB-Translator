package sql.to.mongodb.translator.service.scanner.builders;

import sql.to.mongodb.translator.service.scanner.finite.automata.FSM;

public abstract class FSMBuilder {
    public abstract FSM build();
}
