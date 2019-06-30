package ca.concordia.jaranalyzer.DBModels.jaranalysis.jaranalysis.packageinformation.generated;

import ca.concordia.jaranalyzer.DBModels.jaranalysis.jaranalysis.packageinformation.PackageInformation;
import ca.concordia.jaranalyzer.DBModels.jaranalysis.jaranalysis.packageinformation.PackageInformationImpl;
import com.speedment.common.annotation.GeneratedCode;
import com.speedment.runtime.config.identifier.TableIdentifier;
import com.speedment.runtime.core.component.SqlAdapter;
import com.speedment.runtime.core.db.SqlFunction;

import java.sql.ResultSet;
import java.sql.SQLException;

import static com.speedment.common.injector.State.RESOLVED;

/**
 * The generated Sql Adapter for a {@link
 * ca.concordia.jaranalyzer.DBModels.jaranalysis.jaranalysis.packageinformation.PackageInformation}
 * entity.
 * <p>
 * This file has been automatically generated by Speedment. Any changes made to
 * it will be overwritten.
 * 
 * @author Speedment
 */
@GeneratedCode("Speedment")
public abstract class GeneratedPackageInformationSqlAdapter implements SqlAdapter<PackageInformation> {
    
    private final TableIdentifier<PackageInformation> tableIdentifier;
    
    protected GeneratedPackageInformationSqlAdapter() {
        this.tableIdentifier = TableIdentifier.of("JarAnalysis", "JarAnalysis", "PackageInformation");
    }
    
    protected PackageInformation apply(ResultSet resultSet, int offset) throws SQLException {
        return createEntity()
            .setId(    resultSet.getLong(1 + offset))
            .setJarId( resultSet.getInt(2 + offset))
            .setName(  resultSet.getString(3 + offset))
            ;
    }
    
    protected PackageInformationImpl createEntity() {
        return new PackageInformationImpl();
    }
    
    @Override
    public TableIdentifier<PackageInformation> identifier() {
        return tableIdentifier;
    }
    
    @Override
    public SqlFunction<ResultSet, PackageInformation> entityMapper() {
        return entityMapper(0);
    }
    
    @Override
    public SqlFunction<ResultSet, PackageInformation> entityMapper(int offset) {
        return rs -> apply(rs, offset);
    }
}