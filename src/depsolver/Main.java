package depsolver;

import com.alibaba.fastjson.*;
import com.alibaba.fastjson.TypeReference;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.*;
import java.util.stream.Collectors;

class Package {
    private String name;
    private String version;
    private Integer size;
    private List<List<String>> depends = new ArrayList<>();
    private List<String> conflicts = new ArrayList<>();

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public Integer getSize() {
        return size;
    }

    public List<List<String>> getDepends() {
        return depends;
    }

    public List<String> getConflicts() {
        return conflicts;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public void setSize(Integer size) {
        this.size = size;
    }

    public void setDepends(List<List<String>> depends) {
        this.depends = depends;
    }

    public void setConflicts(List<String> conflicts) {
        this.conflicts = conflicts;
    }
}

class PackageGroup {
    private String name;
    private List<Package> packages = new ArrayList<Package>();;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<Package> getPackages() {
        return packages;
    }

    public void addPackage(Package p) {
        packages.add(p);
    }

    public void setPackages(List<Package> packages) {
        this.packages = packages;
    }
}

public class Main {
    public static List<Package> repo;
    public static ArrayList<String> posConstraints;
    public static ArrayList<String> negConstraints;
    public static ArrayList<String> commands = new ArrayList<String>();

    public static HashSet<List<String>> seenStates = new HashSet<>();
    public static boolean isFinal = false;

    public static void main(String[] args) throws IOException {
        TypeReference<List<Package>> repoType = new TypeReference<List<Package>>() {
        };
        repo = JSON.parseObject(readFile(args[0]), repoType);
        TypeReference<List<String>> strListType = new TypeReference<List<String>>() {
        };
        List<String> initial = JSON.parseObject(readFile(args[1]), strListType);
        List<String> constraints = JSON.parseObject(readFile(args[2]), strListType);

        solve(initial, constraints);
    }

    public static void solve(List<String> initial, List<String> constraints) {
        JSONArray com = new JSONArray();
        if (constraints.size() == 0) {
            System.out.println(com);
            return;
        }

        if (initial.size() == 0) {

        } else {

            System.out.println("Initial state is not empty, unable to solve right now");
            return;
        }

        ArrayList<ArrayList<String>> cons = splitConstraints(constraints);
        posConstraints = cons.get(0);
        negConstraints = cons.get(1);

        repo.sort(Comparator.comparing(Package::getName));
        ArrayList<PackageGroup> packageList = new ArrayList<PackageGroup>();
        for (Package p : repo) {
            if (packageList.stream().anyMatch(packGroup -> p.getName().equals(packGroup.getName()))) {
                PackageGroup packageGroup = packageList.stream()
                        .filter(packGroup -> p.getName().equals(packGroup.getName())).findFirst().orElse(null);
                int index = packageList.indexOf(packageGroup);
                packageGroup.addPackage(p);
                packageList.set(index, packageGroup);
            } else {
                PackageGroup packageGroup = new PackageGroup();
                packageGroup.setName(p.getName());
                packageGroup.addPackage(p);
                packageList.add(packageGroup);
            }
        }

        for (PackageGroup pg : packageList) {
            pg.getPackages().sort(Comparator.comparing(Package::getSize));
        }

        List<String> finalState = depthFirstSearch(initial);

        if(!commands.isEmpty()) {
            System.out.println(JSON.toJSONString(commands));
        }
    }

    public static List<String> depthFirstSearch(List<String> initial) {
        List<String> finalState = new ArrayList<String>();

        finalState = search(initial);

        return finalState;
    }

    public static List<String> search(List<String> state) {
        if (isFinal) {
            return state;
        }

        if (!state.isEmpty() && !isValidState(state)) {  
            return state;
        }

        if (seenStates.contains(state)) {
            return state;
        } else {
            seenStates.add(state);
        }

        if (isFinalState(state)) {
            isFinal = true;
            return state;
        }

        for (Package p : repo) {
            List<String> newState = new ArrayList<String>(state);

            String pack = p.getName() + "=" + p.getVersion();

            if (newState.contains(pack)) {
                newState.remove(pack);
                commands.remove("+" + pack);
            } else {
                newState.add(pack);
                
                if(!commands.contains("+" + pack)) {
                    commands.add("+" + pack);
                }
            }

            List<String> tempState = search(newState);

            if (isFinal) {
                return tempState;
            }
        }

        return state;
    }

    public static boolean isValidState(List<String> state) {
        boolean isValid = true;
        List<Package> packages = new ArrayList<Package>();

        for (String s : state) {
            Package p = parsePackageFromStateString(s);
            packages.add(p);
        }

        for (Package pack : packages) {
            if (pack != null) {
                if (!pack.getConflicts().isEmpty()) {
                    for (String conf : pack.getConflicts()) {
                        if (isConflicting(conf, packages)) {
                            return false;
                        }
                    }
                }
                if (!pack.getDepends().isEmpty()) {
                    for (List<String> depList : pack.getDepends()) {
                        if (isMissingDependency(depList, packages)) {
                            return false;
                        }
                    }
                }
            } else {
                return false;
            }
        }

        return isValid;
    }

    public static boolean isConflicting(String conflict, List<Package> state) {

        if (conflict.contains("<=")) {
            String packageName = conflict.substring(0, conflict.indexOf("<"));
            int packageVersion = Integer.parseInt(conflict.substring(conflict.lastIndexOf("=") + 1));
            
            if (state.stream()
                    .filter(p -> Integer.parseInt(p.getVersion()) <= packageVersion && p.getName().equals(packageName))
                    .findFirst().isPresent()) {
                return true;
            }
        } else if (conflict.contains(">=")) {
            String packageName = conflict.substring(0, conflict.indexOf(">"));
            int packageVersion = Integer.parseInt(conflict.substring(conflict.lastIndexOf("=") + 1));

            if (state.stream()
                    .filter(p -> Integer.parseInt(p.getVersion()) >= packageVersion && p.getName().equals(packageName))
                    .findFirst().isPresent()) {
                return true;
            }
        } else if (conflict.contains("<")) {
            String packageName = conflict.substring(0, conflict.indexOf("<"));
            int packageVersion = Integer.parseInt(conflict.substring(conflict.lastIndexOf("<") + 1));

            if (state.stream()
                    .filter(p -> Integer.parseInt(p.getVersion()) < packageVersion && p.getName().equals(packageName))
                    .findFirst().isPresent()) {
                return true;
            }
        } else if (conflict.contains(">")) {
            String packageName = conflict.substring(0, conflict.indexOf(">"));
            int packageVersion = Integer.parseInt(conflict.substring(conflict.lastIndexOf(">") + 1));

            if (state.stream()
                    .filter(p -> Integer.parseInt(p.getVersion()) > packageVersion && p.getName().equals(packageName))
                    .findFirst().isPresent()) {
                return true;
            }
        } else if (conflict.contains("=")) {
            String packageName = conflict.substring(0, conflict.indexOf("="));
            int packageVersion = Integer.parseInt(conflict.substring(conflict.lastIndexOf("=") + 1));
            if (state.stream()
                    .filter(p -> p.getName().equals(packageName) && Integer.parseInt(p.getVersion()) == packageVersion)
                    .findFirst().isPresent()) {
                return true;
            }
        } else {
            String packageName = conflict;
            if (state.stream().filter(p -> p.getName().equals(packageName)).findFirst().isPresent()) {
                return false;
            }
        }

        return false;
    }

    public static boolean isMissingDependency(List<String> depList, List<Package> state) {
        for (String dep : depList) {
            if (dep.contains("<=")) {
                String packageName = dep.substring(0, dep.indexOf("<"));
                int packageVersion = Integer.parseInt(dep.substring(dep.lastIndexOf("=") + 1));
                if (state.stream().filter(
                        p -> Integer.parseInt(p.getVersion()) <= packageVersion && p.getName().equals(packageName))
                        .findFirst().isPresent()) {
                    return false;
                }
            } else if (dep.contains(">=")) {
                String packageName = dep.substring(0, dep.indexOf(">"));
                int packageVersion = Integer.parseInt(dep.substring(dep.lastIndexOf("=") + 1));

                if (state.stream().filter(
                        p -> Integer.parseInt(p.getVersion()) >= packageVersion && p.getName().equals(packageName))
                        .findFirst().isPresent()) {
                    return false;
                }
            } else if (dep.contains("<")) {
                String packageName = dep.substring(0, dep.indexOf("<"));
                int packageVersion = Integer.parseInt(dep.substring(dep.lastIndexOf("<") + 1));
 
                if (state.stream().filter(
                        p -> Integer.parseInt(p.getVersion()) < packageVersion && p.getName().equals(packageName))
                        .findFirst().isPresent()) {
                    return false;
                }
            } else if (dep.contains(">")) {
                String packageName = dep.substring(0, dep.indexOf(">"));
                int packageVersion = Integer.parseInt(dep.substring(dep.lastIndexOf(">") + 1));

                if (state.stream().filter(
                        p -> Integer.parseInt(p.getVersion()) > packageVersion && p.getName().equals(packageName))
                        .findFirst().isPresent()) {
                    return false;
                }
            } else if (dep.contains("=")) {
                String packageName = dep.substring(0, dep.indexOf("="));
                int packageVersion = Integer.parseInt(dep.substring(dep.lastIndexOf("=") + 1));
                if (state.stream().filter(
                        p -> p.getName().equals(packageName) && Integer.parseInt(p.getVersion()) == packageVersion)
                        .findFirst().isPresent()) {
                    return false;
                }
            } else {
                String packageName = dep;
                if (state.stream().filter(p -> p.getName().equals(packageName)).findFirst().isPresent()) {
                    return false;
                }
            }
        }

        return true;
    }

    public static boolean isFinalState(List<String> state) {
        List<Package> packages = new ArrayList<Package>();

        for (String s : state) {
            Package p = parsePackageFromStateString(s);
            packages.add(p);
        }

        for (String conf : negConstraints) {
            if (isConflicting(conf, packages)) {
                return false;
            }
        }

        for (String dep : posConstraints) {
            List<String> depList = new ArrayList<String>();
            depList.add(dep);

            if (isMissingDependency(depList, packages)) {
                return false;
            }
        }

        isFinal = true;
        return true;
    }

    public static Package parsePackageFromStateString(String state) {
        if (state.contains("=")) {
            String[] pieces = state.split("=");
            String pName = pieces[0];
            String pVer = pieces[1];

            Package pack = repo.stream().filter(p -> p.getName().equals(pName) && p.getVersion().equals(pVer))
                    .findFirst().orElse(null);

            if (pack != null) {
                return pack;
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    public static ArrayList<ArrayList<String>> splitConstraints(List<String> cons) {
        ArrayList<ArrayList<String>> constraints = new ArrayList<ArrayList<String>>();
        ArrayList<String> posCons = new ArrayList<String>();
        ArrayList<String> negCons = new ArrayList<String>();

        for (String con : cons) {
            if (con.charAt(0) == '+') {
                posCons.add(con.substring(1));
            } else if (con.charAt(0) == '-') {
                negCons.add(con.substring(1));
            }
        }

        constraints.add(posCons);
        constraints.add(negCons);

        return constraints;
    }

    static String readFile(String filename) throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(filename));
        StringBuilder sb = new StringBuilder();
        br.lines().forEach(line -> sb.append(line));
        return sb.toString();
    }
}
