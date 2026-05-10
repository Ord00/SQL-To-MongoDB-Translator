package sql.to.mongodb.translator.code.generator;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class TranslationResult {
    private String condition;
    private final List<String> prerequisiteStages;

    public TranslationResult(String condition) {
        this(condition, new ArrayList<>());
    }

    public TranslationResult(String condition, List<String> prerequisiteStages) {
        this.condition = condition;
        this.prerequisiteStages = prerequisiteStages != null ? prerequisiteStages : new ArrayList<>();
    }

    public boolean hasPrerequisiteStages() { return !prerequisiteStages.isEmpty(); }

    public TranslationResult merge(TranslationResult other) {
        List<String> mergedStages = new ArrayList<>(this.prerequisiteStages);
        mergedStages.addAll(other.getPrerequisiteStages());
        return new TranslationResult(this.condition, mergedStages);
    }

    public static TranslationResult empty() {
        return new TranslationResult(null);
    }
}