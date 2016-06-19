package yousei;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathSuffixFilter;
import weka.attributeSelection.BestFirst;
import weka.attributeSelection.CfsSubsetEval;
import weka.classifiers.functions.LinearRegression;
import weka.core.Attribute;
import weka.core.Instances;
import weka.filters.Filter;
import weka.filters.supervised.attribute.AttributeSelection;

import java.io.*;
import java.util.*;


/**
 * Created by s-sumi on 2016/05/23.
 */
public class Util {
    private static File workingDir = new File("WorkingDir");
    public static int smallchange = 0;
    private static final int SMALLTHRESHOLD=5;

    public static Map<String, Integer> addMap(Map<String, Integer> map1, Map<String, Integer> map2) {
        Map<String, Integer> res = new HashMap<>(map1);
        /*for(Map.Entry<String,Integer> e:map2.entrySet()){
            res.merge(e.getKey(), e.getValue(),
                    (a,b)-> a + b );
        }*/
        map2.entrySet().stream()
                .forEach(e -> res.merge(e.getKey(), e.getValue(), (a, b) -> a + b));
        return res;
    }

    public static Map<String, List<Map<String, Integer>>> initGenealogy(RevCommit firstCommit, Repository repo)
            throws IOException, GitAPIException, CoreException {
        RevTree revTree = firstCommit.getTree();
        TreeWalk treeWalk = new TreeWalk(repo);
        treeWalk.addTree(revTree);
        treeWalk.setRecursive(true);
        treeWalk.setFilter(PathSuffixFilter.create(".cpp"));

        Map<String, List<Map<String, Integer>>> genealogy = new HashMap<>();

        while (treeWalk.next()) {
            ObjectId objectId = treeWalk.getObjectId(0);
            ObjectLoader objectLoader = repo.open(objectId);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            objectLoader.copyTo(baos);

            List<Map<String, Integer>> fileGenealogy = new ArrayList<>();
            fileGenealogy.add(getSourceVector(baos.toString()));

            genealogy.put(treeWalk.getPathString(), fileGenealogy);  //mergeでもどっちでもいい
        }
        return genealogy;
    }

    //map返さなくてもいいかも
    public static Map<String, List<Map<String, Integer>>> getGenealogy(RevCommit newRev, Map<String, List<Map<String, Integer>>> genealogy, Repository repo)
            throws Exception {
        File workingDir = new File("WorkingDir");
        CppSourceAnalyzer csa = new CppSourceAnalyzer("", "", "");
        String source;

        RevCommit oldRev = newRev.getParent(0);

        AbstractTreeIterator oldTreeIterator = ChangeAnalyzer.prepareTreeParser(repo,
                oldRev.getId().getName());
        AbstractTreeIterator newTreeIterator = ChangeAnalyzer.prepareTreeParser(repo,
                newRev.getId().getName());
        List<DiffEntry> diff = new Git(repo).diff().setOldTree(oldTreeIterator)
                .setNewTree(newTreeIterator)
                .setPathFilter(PathSuffixFilter.create(".cpp"))
                .call();

        //削除系を先に．リネーム，追加を後に処理
        diff.sort(Util::compareDiffEntries);

        //誠に遺憾ながらcdtはStringを元にASTを構築してくれないので，
        //一旦StringからFileを作成して解析する．終わったら削除
        for (DiffEntry entry : diff) {
            if (entry.getChangeType() == DiffEntry.ChangeType.DELETE) {         //deleteまでの系譜を保存しておきたい
                //TODO
                //if(genealogy.remove(entry.getOldPath())==null)
                //    throw new Exception();//add忘れがあるということ
                genealogy.remove(entry.getOldPath());

            } else if (entry.getChangeType() == DiffEntry.ChangeType.RENAME) {
                List<Map<String, Integer>> tmp = genealogy.get(entry.getOldPath());
                genealogy.remove(entry.getOldPath());
                genealogy.put(entry.getNewPath(), tmp);
            } else if (entry.getChangeType() == DiffEntry.ChangeType.MODIFY) {
                ObjectLoader olnew;
                ByteArrayOutputStream bosnew = new ByteArrayOutputStream();

                if (!entry.getNewId().toObjectId().equals(ObjectId.zeroId())) { // NEWが存在するか
                    olnew = repo.open(entry.getNewId().toObjectId()); // ソースを読み込んで，コメントなどを消去
                    olnew.copyTo(bosnew);
                    source = bosnew.toString();
                } else {
                    continue;
                }

                if (Objects.equals(source, ""))
                    continue;

                File tmpFile = File.createTempFile("new", ".cpp", workingDir);
                BufferedWriter bw = new BufferedWriter(new FileWriter(tmpFile));
                bw.write(source);
                bw.close();


                csa.setFilePath(tmpFile.getAbsolutePath());
                Map<String, Integer> res = csa.analyzeFile();
                tmpFile.delete();

                if (Objects.equals(entry.getOldPath(), entry.getNewPath())) {
                    List<Map<String, Integer>> tmp = genealogy.get(entry.getOldPath());
                    tmp.add(res);
                    genealogy.put(entry.getOldPath(), tmp);
                } else {
                    List<Map<String, Integer>> tmp = genealogy.get(entry.getOldPath());
                    tmp.add(res);
                    genealogy.remove(entry.getOldPath());
                    genealogy.put(entry.getNewPath(), tmp);
                }

            } else if (entry.getChangeType() == DiffEntry.ChangeType.ADD) {
                ObjectLoader olnew;
                ByteArrayOutputStream bosnew = new ByteArrayOutputStream();

                if (!entry.getNewId().toObjectId().equals(ObjectId.zeroId())) { // NEWが存在するか
                    olnew = repo.open(entry.getNewId().toObjectId()); // ソースを読み込んで，コメントなどを消去
                    olnew.copyTo(bosnew);
                    source = bosnew.toString();
                } else {
                    continue;
                }

                if (Objects.equals(source, ""))
                    continue;

                File tmpFile = File.createTempFile("new", ".cpp", workingDir);
                BufferedWriter bw = new BufferedWriter(new FileWriter(tmpFile));
                bw.write(source);
                bw.close();


                csa.setFilePath(tmpFile.getAbsolutePath());
                Map<String, Integer> res = csa.analyzeFile();
                tmpFile.delete();

                List<Map<String, Integer>> tmp = new ArrayList<>();

                tmp.add(res);
                genealogy.put(entry.getNewPath(), tmp);
            } else {      //copy
                List<Map<String, Integer>> tmp = new ArrayList<>(genealogy.get(entry.getOldPath()));
                genealogy.put(entry.getNewPath(), tmp);
            }

        }
        return genealogy;
    }

    public static int convertTypeToInteger(DiffEntry.ChangeType ct) {
        if (ct == DiffEntry.ChangeType.DELETE) {
            return 0;
        } else if (ct == DiffEntry.ChangeType.RENAME) {
            return 1;
        } else if (ct == DiffEntry.ChangeType.MODIFY) {
            return 2;
        } else if (ct == DiffEntry.ChangeType.ADD) {
            return 3;
        } else {//copy
            return 4;
        }
    }

    public static int compareDiffEntries(DiffEntry a, DiffEntry b) {
        return convertTypeToInteger(a.getChangeType()) - convertTypeToInteger(b.getChangeType());
    }

    public static Map<String, Integer> getSourceVector(String source) throws IOException, CoreException {
        File tmpFile = File.createTempFile("tmp", ".cpp", workingDir);
        BufferedWriter bw = new BufferedWriter(new FileWriter(tmpFile));
        bw.write(source);
        bw.close();
        CppSourceAnalyzer csa = new CppSourceAnalyzer("", "", "");
        csa.setFilePath(tmpFile.getAbsolutePath());
        return csa.analyzeFile();
    }

    /**
     * mapで表された状態ベクトルをListに変換する
     *
     * @param vector
     * @return
     */
    public static List<Integer> convertVector2List(Map<String, Integer> vector) {
        NodeClasses nc = new NodeClasses();
        List<Integer> list = new ArrayList<>(Collections.nCopies(nc.dictionary.size(), 0));
        for (Map.Entry<String, Integer> e : vector.entrySet()) {
            Integer i = nc.dictionary.get(e.getKey());
            i = i == null ? nc.dictionary.size() : i;
            list.set(i, e.getValue());
        }
        return list;
    }

    public static File singleGenealogy2Arff(List<Map<String, Integer>> genealogy) throws IOException {
        NodeClasses nc = new NodeClasses();
        File tmpFile = File.createTempFile("genealogy", ".arff", workingDir);
        BufferedWriter bw = new BufferedWriter(new FileWriter(tmpFile));
        bw.write("@relation StateVector");
        bw.newLine();
        bw.newLine();
        for (Map.Entry<String, Integer> e : nc.dictionary.entrySet()) {
            bw.write("@attribute " + e.getKey() + " numeric");
            bw.newLine();
        }
        for (Map.Entry<String, Integer> e : nc.dictionary.entrySet()) {
            bw.write("@attribute " + e.getKey() + "2 numeric");
            bw.newLine();
        }

        //bw.write("@attribute UNKNOWN numeric");
        //bw.newLine();
        bw.newLine();

        bw.write("@data");
        bw.newLine();
        if (genealogy.size() < 2) {
            bw.close();
            return tmpFile;
        }
        List<Integer> pre;
        List<Integer> now = convertVector2List(genealogy.get(0));
        for (int i = 1; i < genealogy.size(); i++) {//系譜の長さ分ループ
            pre = now;
            now = convertVector2List(genealogy.get(i));
            writeVector(bw, pre);
            bw.write(",");
            writeVector(bw, now);
            bw.newLine();
        }

        bw.close();
        return tmpFile;
    }

    public static void writeVector(BufferedWriter bw, List<Integer> vector) throws IOException {
        for (int i = 0; i < vector.size(); i++) {
            bw.write(vector.get(i).toString());
            if (i != vector.size() - 1)
                bw.write(",");
        }
    }
    //Logging処理が埋め込まれてて読みにくい
    public static void predict(File data) throws Exception {
        BufferedReader br = new BufferedReader(new FileReader(data));
        Instances instances = new Instances(br);
        int num = instances.numAttributes() / 2;
        List<Integer> changedNum=Util.changedDataCounter(instances);
        br.close();

        File f=new File("results/resCV.csv");
        BufferedWriter resBw=new BufferedWriter(new FileWriter(f));
        resBw.write("prediction result summary\n");
        resBw.write("node name,num_classified,num_correct,num_incorrect,precision,num_changed\n");

        for (int i = 0; i < num; i++) {
            br = new BufferedReader(new FileReader(data));
            instances = new Instances(br);
            Attribute attr = instances.attribute(i);
            String attrName = attr.name();
            instances = Util.removeAttrWithoutI(i, instances);
            instances.setClassIndex(num);
            instances = useFilter(instances);

            LinearRegression lr = new LinearRegression();
            String[] options = {};
            lr.setOptions(options);
            lr.buildClassifier(instances);

            CustomizedCrossValidation ccv = new CustomizedCrossValidation();
            ccv.evaluate(lr, instances, 10, new Random(1));
            resBw.write(attrName);resBw.write(",");
            resBw.write(Integer.toString(ccv.num_classified));resBw.write(",");
            resBw.write(Integer.toString(ccv.num_correct));resBw.write(",");
            resBw.write(Integer.toString(ccv.num_incorrect));resBw.write(",");
            resBw.write(String.valueOf((double)ccv.num_correct/(double)ccv.num_classified));resBw.write(",");
            resBw.write(Integer.toString(changedNum.get(i)));

            resBw.newLine();
            System.out.println("end");
            br.close();
        }
        resBw.close();
    }
    public static List<Integer> changedDataCounter(Instances data){
        int num=data.numAttributes()/2;
        List<Integer> res=new ArrayList<>(Collections.nCopies(num,0));
        for(int i=0;i<num;i++){
            for (int j = 0; j < data.numInstances(); j++) {
                if (data.instance(j).value(i) != data.instance(j).value(i + num)) {
                    res.set(i, res.get(i) + 1);
                    break;
                }
            }
        }
        return res;
    }
/*
    public static void vectoredPrediction(File data, BufferedWriter resBw) throws Exception {
        BufferedReader br = new BufferedReader(new FileReader(data));
        Instances instances = new Instances(br);
        int num = instances.numAttributes() / 2;
        br.close();
        List<Classifier> classifiers=new ArrayList<>();
        List<Instances> filteredDatas=new ArrayList<>();
        for (int i = 0; i < num; i++) {
            br = new BufferedReader(new FileReader(data));
            instances = new Instances(br);
            Attribute attr = instances.attribute(i);
            instances = Util.removeAttrWithoutI(i, instances);
            instances.setClassIndex(num);
            instances = useFilter(instances, i);

            LinearRegression lr = new LinearRegression();
            String[] options = {};
            lr.setOptions(options);
            lr.buildClassifier(instances);
            classifiers.add(lr);
            filteredDatas.add(instances.);
            br.close();
        }
        //resBw.close();
    }*/

    /**
     * ２つ目の状態ベクトルの
     * @param i
     * @param instances
     * @return
     */
    public static Instances removeAttrWithoutI(int i, Instances instances) {
        int num = instances.numAttributes() / 2;
        int counter = 0;
        boolean skipped = false;
        while (counter != num) {
            if (counter != i) {
                if (!skipped) {
                    instances.deleteAttributeAt(num);
                } else {
                    instances.deleteAttributeAt(num + 1);
                }
            } else {
                skipped = true;
            }
            counter++;

        }
        return instances;
    }

    public static File allGenealogy2Arff(Map<String, List<Map<String, Integer>>> genealogy) throws IOException {
        NodeClasses nc = new NodeClasses();
        File tmpFile = File.createTempFile("genealogy", ".arff", workingDir);
        BufferedWriter bw = new BufferedWriter(new FileWriter(tmpFile));
        bw.write("@relation StateVector");
        bw.newLine();
        bw.newLine();
        for (Map.Entry<String, Integer> e : nc.dictionary.entrySet()) {
            bw.write("@attribute " + e.getKey() + " numeric");
            bw.newLine();
        }
        for (Map.Entry<String, Integer> e : nc.dictionary.entrySet()) {
            bw.write("@attribute " + e.getKey() + "2 numeric");
            bw.newLine();
        }
        bw.newLine();

        bw.write("@data");
        bw.newLine();
        for (Map.Entry<String, List<Map<String, Integer>>> e : genealogy.entrySet()) {
            if (e.getValue().size() < 2)
                continue;
            List<Integer> pre;
            List<Integer> now = convertVector2List(e.getValue().get(0));
            for (int i = 1; i < e.getValue().size(); i++) {//系譜の長さ分ループ
                pre = now;
                now = convertVector2List(e.getValue().get(i));
                if (diffIsBig(pre, now, SMALLTHRESHOLD))
                    continue;
                smallchange++;
                writeVector(bw, pre);
                bw.write(",");
                writeVector(bw, now);
                bw.newLine();
            }
        }

        bw.close();
        return tmpFile;
    }

    public static Instances useFilter(Instances data) throws Exception {
        AttributeSelection filter = new AttributeSelection();
        CfsSubsetEval eval = new CfsSubsetEval();
        BestFirst search = new BestFirst();
        String[] options = {"-D", "1", "-N", "5"};
        search.setOptions(options);
        filter.setEvaluator(eval);
        filter.setInputFormat(data);
        Instances newData = Filter.useFilter(data, filter);
        newData.setClassIndex(newData.numAttributes() - 1);
        //if (newData.classIndex() == -1)
        //    newData.setClassIndex(predictNum);
        return newData;
    }

    public static void enumNotFoundNodes(Set<String> set) {
        System.out.println("\nNotFoundNodes:");
        NodeClasses nc = new NodeClasses();
        for (String s : set) {
            if (!nc.dictionary.containsKey(s)) {
                System.out.println(s);
            }
        }
    }

    public static boolean diffIsBig(List<Integer> a, List<Integer> b, int threshold) {
        int counter = 0;
        for (int i = 0; i < a.size(); i++) {
            counter += Math.abs(a.get(i) - b.get(i));
        }
        return counter > threshold;
    }


}
