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

public class Main {
    public static List<Package> repo;
    public static ArrayList<String> posConstraints;
    public static ArrayList<String> negConstraints;
    public static List<List<String>> finalStates = new ArrayList<>();

    public static HashSet<List<String>> seenStates = new HashSet<>();
    public static boolean isFinal = false;
    public static String vers;

    public static final int UNINSTALL_SIZE = 1000000;

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
            System.out.println(JSON.toJSONString(com));
            return;
        }

        if (initial.size() != 0) {
            if (!isValidState(initial)) {
                System.out.println("Initial state is not valid, ignore this for now");
                return;
            }
        }

        ArrayList<ArrayList<String>> cons = splitConstraints(constraints);
        posConstraints = cons.get(0);
        negConstraints = cons.get(1);

        seenStates.add(initial);
        search(initial);

        List<String> bestFinalState = finalStates.get(0);
            List<String> bestCommands = new ArrayList<String>();
            int bestSize = 0;

            List<Package> initialPackages = new ArrayList<Package>();

            for (String s : initial) {
                Package p = parsePackageFromStateString(s);
                initialPackages.add(p);
            }

            for (List<String> fState : finalStates) {
                List<String> currentState = new ArrayList<String>(initial);
                List<Package> currentPackages = new ArrayList<Package>(initialPackages);
                List<String> currentCommands = new ArrayList<String>();
                int currentSize = 0;

                for (String s : fState) {
                    Package pack = parsePackageFromStateString(s);
                    currentState.add(s);

                    List<String> conflicts = pack.getConflicts();

                    for (String conflict : conflicts) {
                        currentPackages.add(pack);

                        if (isConflicting(conflict, currentPackages) || isConflicting(conflict, initialPackages)) {
                            if (conflict.contains("<=")) {
                                String packageName = conflict.substring(0, conflict.indexOf("<"));
                                String packageVersion = conflict.substring(conflict.lastIndexOf("=") + 1);

                                if (currentPackages.stream()
                                        .filter(p -> compareVersions(p.getVersion(), packageVersion, "<=")
                                                && p.getName().equals(packageName))
                                        .findFirst().isPresent()) {
                                    Package foundPackage = currentPackages.stream()
                                            .filter(p -> compareVersions(p.getVersion(), packageVersion, "<=")
                                                    && p.getName().equals(packageName))
                                            .findFirst().orElse(null);

                                    currentCommands.add("-" + foundPackage.getName() + "=" + foundPackage.getVersion());
                                    currentSize += UNINSTALL_SIZE;
                                }
                            } else if (conflict.contains(">=")) {
                                String packageName = conflict.substring(0, conflict.indexOf(">"));
                                String packageVersion = conflict.substring(conflict.lastIndexOf("=") + 1);

                                if (currentPackages.stream()
                                        .filter(p -> compareVersions(p.getVersion(), packageVersion, ">=")
                                                && p.getName().equals(packageName))
                                        .findFirst().isPresent()) {
                                    Package foundPackage = currentPackages.stream()
                                            .filter(p -> compareVersions(p.getVersion(), packageVersion, ">=")
                                                    && p.getName().equals(packageName))
                                            .findFirst().orElse(null);

                                    currentCommands.add("-" + foundPackage.getName() + "=" + foundPackage.getVersion());
                                    currentSize += UNINSTALL_SIZE;
                                }
                            } else if (conflict.contains("<")) {
                                String packageName = conflict.substring(0, conflict.indexOf("<"));
                                String packageVersion = conflict.substring(conflict.lastIndexOf("<") + 1);

                                if (currentPackages.stream()
                                        .filter(p -> compareVersions(p.getVersion(), packageVersion, "<")
                                                && p.getName().equals(packageName))
                                        .findFirst().isPresent()) {
                                    Package foundPackage = currentPackages.stream()
                                            .filter(p -> compareVersions(p.getVersion(), packageVersion, "<")
                                                    && p.getName().equals(packageName))
                                            .findFirst().orElse(null);

                                    currentCommands.add("-" + foundPackage.getName() + "=" + foundPackage.getVersion());
                                    currentSize += UNINSTALL_SIZE;
                                }
                            } else if (conflict.contains(">")) {
                                String packageName = conflict.substring(0, conflict.indexOf(">"));
                                String packageVersion = conflict.substring(conflict.lastIndexOf(">") + 1);

                                if (currentPackages.stream()
                                        .filter(p -> compareVersions(p.getVersion(), packageVersion, ">")
                                                && p.getName().equals(packageName))
                                        .findFirst().isPresent()) {

                                    Package foundPackage = currentPackages.stream()
                                            .filter(p -> compareVersions(p.getVersion(), packageVersion, ">")
                                                    && p.getName().equals(packageName))
                                            .findFirst().orElse(null);

                                    currentCommands.add("-" + foundPackage.getName() + "=" + foundPackage.getVersion());
                                    currentSize += UNINSTALL_SIZE;
                                }
                            } else if (conflict.contains("=")) {
                                String packageName = conflict.substring(0, conflict.indexOf("="));
                                String packageVersion = conflict.substring(conflict.lastIndexOf("=") + 1);

                                if (currentPackages.stream()
                                        .filter(p -> compareVersions(p.getVersion(), packageVersion, "=")
                                                && p.getName().equals(packageName))
                                        .findFirst().isPresent()) {
                                    Package foundPackage = currentPackages.stream()
                                            .filter(p -> compareVersions(p.getVersion(), packageVersion, "=")
                                                    && p.getName().equals(packageName))
                                            .findFirst().orElse(null);

                                    currentCommands.add("-" + foundPackage.getName() + "=" + foundPackage.getVersion());
                                    currentSize += UNINSTALL_SIZE;
                                }
                            } else {

                                if (currentPackages.stream().filter(p -> p.getName().equals(conflict)).findFirst()
                                        .isPresent()) {

                                    List<Package> cs = currentPackages.stream()
                                            .filter(p -> p.getName().equals(conflict)).collect(Collectors.toList());

                                    for (Package cPack : cs) {
                                        currentCommands.add("-" + cPack.getName() + "=" + cPack.getVersion());
                                        currentSize += UNINSTALL_SIZE;
                                    }
                                }
                            }
                        }
                    }

                    currentCommands.add("+" + pack.getName() + "=" + pack.getVersion());
                    currentSize += pack.getSize();
                }

                if (currentSize < bestSize || bestSize == 0) {
                    bestSize = currentSize;
                    bestFinalState = fState;
                    bestCommands = currentCommands;
                }
            }

            System.out.println(JSON.toJSONString(bestCommands));

    }

    public static void search(List<String> state) {
        // TODO: This could catch me out, if the starting state isn't valid to begin
        // with? - Becareful of this maybe
        if (!state.isEmpty() && !isValidState(state)) {
            return;
        }

        if (!state.isEmpty() && isFinalState(state)) {
            finalStates.add(new ArrayList<String>(state));

            return;
        }

        if(state.size() > repo.size()) {
            return;
        }

        for (Package p : repo) {
            ArrayList<String> newState = new ArrayList<>(state);
            String pack = p.getName() + "=" + p.getVersion();

            if (newState.contains(pack)) {
                newState.remove(pack);
            } else {
                newState.add(pack);
            }

            if (seenStates.contains(newState)) {
                continue;
            } else {
                seenStates.add(newState);
                search(newState);
            }            
        }

        return;
    }

    public static boolean isValidState(List<String> state) {
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
                // Package does not exist, so it is wrong? - This should not ever happen, more
                // of a fail safe I guess?
                return false;
            }
        }

        return true;
    }

    public static boolean compareVersions(String v1, String v2, String comparison) {
        String[] v1Parts = v1.split("\\.");
        String[] v2Parts = v2.split("\\.");

        int length = Math.max(v1Parts.length, v2Parts.length);

        for (int i = 0; i < length; i++) {
            int v1Part = i < v1Parts.length ? Integer.parseInt(v1Parts[i]) : 0;
            int v2Part = i < v2Parts.length ? Integer.parseInt(v2Parts[i]) : 0;

            if (comparison == "<=") {
                if (v1Part > v2Part) {
                    return false;
                }
            } else if (comparison == ">=") {
                if (v1Part < v2Part) {
                    return false;
                }
            } else if (comparison == "<") {
                if (v1Part < v2Part) {
                    return true;
                } else if (i == length - 1 && v1Part >= v2Part) {
                    return false;
                }
            } else if (comparison == ">") {
                if (v1Part > v2Part) {
                    return true;
                } else if (i == length - 1 && v1Part <= v2Part) {
                    return false;
                }
            } else if (comparison == "=") {
                if (v1Part != v2Part) {
                    return false;
                }
            }
        }

        vers = v1;

        // System.out.println(v1 + " is " + comparison + v2);
        return true;
    }

    public static boolean isConflicting(String conflict, List<Package> state) {
        if (conflict.contains("<=")) {
            String packageName = conflict.substring(0, conflict.indexOf("<"));
            String packageVersion = conflict.substring(conflict.lastIndexOf("=") + 1);

            for(Package p : state) {
                if(p.getName().equals(packageName) && compareVersions(p.getVersion(), packageVersion, "<=")) {
                    return true;
                }
            }
        } else if (conflict.contains(">=")) {
            String packageName = conflict.substring(0, conflict.indexOf(">"));
            String packageVersion = conflict.substring(conflict.lastIndexOf("=") + 1);

            for(Package p : state) {
                if(p.getName().equals(packageName) && compareVersions(p.getVersion(), packageVersion, ">=")) {
                    return true;
                }
            }
        } else if (conflict.contains("<")) {
            String packageName = conflict.substring(0, conflict.indexOf("<"));
            String packageVersion = conflict.substring(conflict.lastIndexOf("<") + 1);

            for(Package p : state) {
                if(p.getName().equals(packageName) && compareVersions(p.getVersion(), packageVersion, "<")) {
                    return true;
                }
            }
        } else if (conflict.contains(">")) {
            String packageName = conflict.substring(0, conflict.indexOf(">"));
            String packageVersion = conflict.substring(conflict.lastIndexOf(">") + 1);

            for(Package p : state) {
                if(p.getName().equals(packageName) && compareVersions(p.getVersion(), packageVersion, ">")) {
                    return true;
                }
            }
        } else if (conflict.contains("=")) {
            String packageName = conflict.substring(0, conflict.indexOf("="));
            String packageVersion = conflict.substring(conflict.lastIndexOf("=") + 1);

            for(Package p : state) {
                if(p.getName().equals(packageName) && compareVersions(p.getVersion(), packageVersion, "=")) {
                    return true;
                }
            }
        } else {
            String packageName = conflict;

            for(Package p : state) {
                if(p.getName().equals(packageName)) {
                    return true;
                }
            }
        }

        return false;
    }

    public static boolean isMissingDependency(List<String> depList, List<Package> state) {
        for (String dep : depList) {
            if (dep.contains("<=")) {
                String packageName = dep.substring(0, dep.indexOf("<"));
                String packageVersion = dep.substring(dep.lastIndexOf("=") + 1);

                for(Package p : state) {
                    if(p.getName().equals(packageName) && compareVersions(p.getVersion(), packageVersion, "<=")) {
                        return false;
                    }
                }
            } else if (dep.contains(">=")) {
                String packageName = dep.substring(0, dep.indexOf(">"));
                String packageVersion = dep.substring(dep.lastIndexOf("=") + 1);

                for(Package p : state) {
                    if(p.getName().equals(packageName) && compareVersions(p.getVersion(), packageVersion, ">=")) {
                        return false;
                    }
                }
            } else if (dep.contains("<")) {
                String packageName = dep.substring(0, dep.indexOf("<"));
                String packageVersion = dep.substring(dep.lastIndexOf("<") + 1);

                for(Package p : state) {
                    if(p.getName().equals(packageName) && compareVersions(p.getVersion(), packageVersion, "<")) {
                        return false;
                    }
                }
            } else if (dep.contains(">")) {
                String packageName = dep.substring(0, dep.indexOf(">"));
                String packageVersion = dep.substring(dep.lastIndexOf(">") + 1);

                for(Package p : state) {
                    if(p.getName().equals(packageName) && compareVersions(p.getVersion(), packageVersion, ">")) {
                        return false;
                    }
                }
            } else if (dep.contains("=")) {
                String packageName = dep.substring(0, dep.indexOf("="));
                String packageVersion = dep.substring(dep.lastIndexOf("=") + 1);

                for(Package p : state) {
                    if(p.getName().equals(packageName) && compareVersions(p.getVersion(), packageVersion, "=")) {
                        return false;
                    }
                }
            } else {
                String packageName = dep;

                for(Package p : state) {
                    if(p.getName().equals(packageName)) {
                        return false;
                    }
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

        return true;
    }

    public static Package parsePackageFromStateString(String state) {
        Package pack = null;
        if (state.contains("=")) {
            String[] pieces = state.split("=");
            String pName = pieces[0];
            String pVer = pieces[1];

            for(Package p : repo) {
                if(p.getName().equals(pName) && p.getVersion().equals(pVer)) {
                    pack = p;
                    break;
                }
            }

            return pack;
        } else {
            return pack;
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
