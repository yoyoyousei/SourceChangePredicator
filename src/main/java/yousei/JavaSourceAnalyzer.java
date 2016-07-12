package yousei;

import org.eclipse.cdt.core.dom.ast.IASTTranslationUnit;
import org.eclipse.cdt.core.dom.ast.gnu.c.GCCLanguage;
import org.eclipse.cdt.core.index.IIndex;
import org.eclipse.cdt.core.model.ILanguage;
import org.eclipse.cdt.core.parser.*;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;

import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by s-sumi on 2016/07/12.
 */
public class JavaSourceAnalyzer {

    private String filePath;	//基本これのみ必要
    private String classPath;
    private String outputPath;

    public JavaSourceAnalyzer(String filePath, String classPath,
                              String outputPath) {
        super();
        this.filePath = filePath;
        this.classPath = classPath;
        this.outputPath = outputPath;
    }
    public List<Integer> analyzeFile() throws IOException,CoreException {
        StringBuilder source = new StringBuilder();
        try(FileReader fr = new FileReader(filePath)) {
            int c;
            while ((c = fr.read()) >= 0) {
                source.append((char)c);
            }
        }
        ASTParser parser = ASTParser.newParser(AST.JLS4);
        parser.setSource(source.toString().toCharArray());
        CompilationUnit unit = (CompilationUnit) parser
                .createAST(new org.eclipse.core.runtime.NullProgressMonitor());

        JavaSourceVisitor visitor = new JavaSourceVisitor();
        unit.accept(visitor);
        return new ArrayList<>(visitor.vector);
    }
    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }
    public void setOutputPath(String outputPath) {
        this.outputPath = outputPath;
    }



}
