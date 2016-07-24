package yousei.experiment;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.filter.PathSuffixFilter;
import yousei.GeneralUtil;
import yousei.util.Util;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Created by s-sumi on 2016/07/12.
 */
public class RepositoryAnalyzer4Java extends RepositoryAnalyzerForBugfix {

    public List<List<Integer>> preVector4Java = new ArrayList<>();
    public List<List<Integer>> postVector4Java = new ArrayList<>();

    public RepositoryAnalyzer4Java(String reposPath) throws Exception {
        super(reposPath);
    }

    @Override
    public void analyzeRepository(String resultPath) throws Exception {
        RevWalk rw = getInitializedRevWalk(this.repository, RevSort.REVERSE);//最古
        RevCommit commit = rw.next();
        while (commit != null) {
            if (commit.getParentCount() >= 1 /*&& Util.isBugfix(commit.getFullMessage())*/) {
                updateGenealogy(commit);
            }
            commit = rw.next();
        }
        File f = Util.allGenealogy2Arff4Java(preVector4Java,postVector4Java);
        Util.predict(f, resultPath, false);
        Util.predict(f, resultPath, true);
        Util.vectoredPrediction(f, resultPath, false);
        Util.vectoredPrediction(f, resultPath, true);
        //Util.predictWithSomeClassifiers(f,resultPath,classifiers,false);
        //Util.vectoredPredictionWithSomeClassifiers(f,resultPath,classifiers,false);
        f.delete();
    }

    @Override
    public void updateGenealogy(RevCommit newRev) throws Exception {
        updateForGivenSuffix(newRev, ".java");
    }

    @Override
    public void initGenealogy(RevCommit firstCommit) throws Exception {
        initForGivenSuffix(firstCommit,".java");
    }

    //newRevは1つ以上の親コミットを持つこと
    @Override
    public void updateForGivenSuffix(RevCommit newRev, String suffix) throws Exception {
        RevCommit oldRev = newRev.getParent(0);

        AbstractTreeIterator oldTreeIterator = ChangeAnalyzer.prepareTreeParser(repository,
                oldRev.getId().getName());
        AbstractTreeIterator newTreeIterator = ChangeAnalyzer.prepareTreeParser(repository,
                newRev.getId().getName());
        List<DiffEntry> diff = new Git(repository).diff().setOldTree(oldTreeIterator)
                .setNewTree(newTreeIterator)
                .setPathFilter(PathSuffixFilter.create(suffix))
                .call();

        for (DiffEntry entry : diff) {
            if (entry.getChangeType() == DiffEntry.ChangeType.MODIFY) {
                ObjectLoader olold;
                ByteArrayOutputStream bosold = new ByteArrayOutputStream();
                String oldSource;

                if (!entry.getNewId().toObjectId().equals(ObjectId.zeroId())) { // OLDが存在するか
                    olold = repository.open(entry.getNewId().toObjectId());
                    olold.copyTo(bosold);
                    oldSource = bosold.toString();
                } else {
                    continue;
                }

                ObjectLoader olnew;
                ByteArrayOutputStream bosnew = new ByteArrayOutputStream();
                String newSource;
                if (!entry.getNewId().toObjectId().equals(ObjectId.zeroId())) { // NEWが存在するか
                    olnew = repository.open(entry.getNewId().toObjectId());
                    olnew.copyTo(bosnew);
                    newSource = bosnew.toString();
                } else {
                    continue;
                }

                if (Objects.equals(oldSource, "") || Objects.equals(newSource, "")) //ソースの修正なら解析対象とする
                    continue;

                preVector4Java.add(GeneralUtil.getSourceVector4Java(oldSource, suffix));
                postVector4Java.add(GeneralUtil.getSourceVector4Java(newSource, suffix));

            }
        }
    }
}
