package ca.concordia.jaranalyzer.DBModels.jaranalysis.jaranalysis.commitsjar.generated;

import ca.concordia.jaranalyzer.DBModels.jaranalysis.jaranalysis.commitsjar.CommitsJar;
import ca.concordia.jaranalyzer.DBModels.jaranalysis.jaranalysis.commitsjar.CommitsJarManager;
import com.speedment.common.annotation.GeneratedCode;
import com.speedment.runtime.config.identifier.TableIdentifier;
import com.speedment.runtime.core.manager.AbstractManager;
import com.speedment.runtime.field.Field;

import java.util.stream.Stream;

/**
 * The generated base implementation for the manager of every {@link
 * ca.concordia.jaranalyzer.DBModels.jaranalysis.jaranalysis.commitsjar.CommitsJar}
 * entity.
 * <p>
 * This file has been automatically generated by Speedment. Any changes made to
 * it will be overwritten.
 * 
 * @author Speedment
 */
@GeneratedCode("Speedment")
public abstract class GeneratedCommitsJarManagerImpl 
extends AbstractManager<CommitsJar> 
implements GeneratedCommitsJarManager {
    
    private final TableIdentifier<CommitsJar> tableIdentifier;
    
    protected GeneratedCommitsJarManagerImpl() {
        this.tableIdentifier = TableIdentifier.of("JarAnalysis", "JarAnalysis", "CommitsJar");
    }
    
    @Override
    public TableIdentifier<CommitsJar> getTableIdentifier() {
        return tableIdentifier;
    }
    
    @Override
    public Stream<Field<CommitsJar>> fields() {
        return CommitsJarManager.FIELDS.stream();
    }
    
    @Override
    public Stream<Field<CommitsJar>> primaryKeyFields() {
        return Stream.empty();
    }
}