package com.sxw.server.util;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class NodeTreeUtil {

    private static String listToString(Object[] a){
        if (a == null)
            return "null";
        int iMax = a.length - 1;
        if (iMax == -1)
            return "null";
        StringBuilder b = new StringBuilder();
        for (int i = 0; ; i++) {
            b.append(String.valueOf(a[i]));
            if (i == iMax)
                return b.toString();
            b.append(",");
        }
    }
    public static void main(String[] args) {
        ConcurrentHashMap<String,String> data = new ConcurrentHashMap<String,String>();
        data.put("30","4");
        data.put("1","0");
        data.put("2","0");
        data.put("3","1");
        data.put("4","1");
        data.put("8","2");
        data.put("9","2");
        data.put("5","3");
        data.put("6","4");
        data.put("7","4");
        data.put("11","10");
        data.put("12","10");
        data.put("13","11");
        data.put("14","11");
        data.put("15","12");
        data.put("16","12");
        data.put("17","16");
        data.put("18","16");
        data.put("20","5");
        data.put("100","99");
        System.out.println(listToString(TreeBuilder.getLeafs(TreeBuilder.createOneTree(data,"0")).toArray()));
    }


    public static class Tag{
        private String id;
        private String pid;
        public Tag(String id, String pid) {
            this.id = id;
            this.pid = pid;
        }
        public String getId() {
            return id;
        }
        public void setId(String id) {
            this.id = id;
        }
        public String getPid() {
            return pid;
        }
        public void setPid(String pid) {
            this.pid = pid;
        }
    }

    public static class TagTreeNode extends DefaultMutableTreeNode {
        public TagTreeNode() {
            super();
        }
        public TagTreeNode(Object userObject) {
            super(userObject);
        }
    }

    public static class TreeBuilder{
        public static List<String> getLeafs(List<TagTreeNode> rootNodes){
            List<String> leafs =  new ArrayList<>();
            for (TagTreeNode root: rootNodes){
                nextTry(root,leafs);
            }
            for (String leaf:leafs){
                System.out.println(leaf);
            }
            return leafs;
        }

        public static List<String> getLeafs(TagTreeNode rootNode){
            List<String> leafs =  new ArrayList<>();
            nextTry(rootNode,leafs);
            for (String leaf:leafs){
                System.out.println(leaf);
            }
            return leafs;
        }

        private static void nextTry(TagTreeNode node,List<String> leafs) {
            if (node.isLeaf()){
                Tag tag = (Tag) node.getUserObject();
                leafs.add(tag.id);
            }else {
                Enumeration<TagTreeNode> childs = node.children();
                while (childs.hasMoreElements()){
                    nextTry(childs.nextElement(),leafs);
                }
            }
        }

        public static TagTreeNode createOneTree(ConcurrentHashMap<String,String> tags, String root){
            TagTreeNode rootNode = createNode(new Tag(root,tags.get(root)));
            List<Tag> tagList =  new LinkedList<>();
            tags.entrySet().forEach(
                    e -> {
                        tagList.add(new Tag(e.getKey(),e.getValue()));
                    }
            );
            Map<String, List<TagTreeNode>> childNodes = getChildNodes(tagList);
            Queue<TagTreeNode> queue = new LinkedList<>();
            queue.add(rootNode);
            while (!queue.isEmpty()) {
                TagTreeNode node = queue.poll();
                Tag tag = (Tag) node.getUserObject();
                List<TagTreeNode> children = childNodes.get(tag.getId());
                if (children != null && !children.isEmpty()) {
                    // Set children
                    for (TagTreeNode child : children) {
                        node.add(child);
                        queue.add(child);
                    }
                }
            }
            return rootNode;
        }

        // tags: <id,pid>
        public static List<TagTreeNode> createTrees(HashMap<String,String> tags) {
            // 所有的根节点
            Object[] rootPids = tags.values().stream().distinct().filter(e -> !tags.keySet().contains(e)).toArray();
            List<TagTreeNode> rootNodes =  new ArrayList<>();
            for (Object rootPid:rootPids){
                for(String key: tags.keySet()){
                    if (rootPid.equals(tags.get(key))){
                        rootNodes.add(createNode(new Tag(key,rootPid.toString())));
                    }
                }
            }
            List<Tag> tagList =  new LinkedList<>();
            tags.entrySet().forEach(
                    e -> {
                        tagList.add(new Tag(e.getKey(),e.getValue()));
                    }
            );
            Map<String, List<TagTreeNode>> childNodes = getChildNodes(tagList);
            for (TagTreeNode rootNode:rootNodes){
                Queue<TagTreeNode> queue = new LinkedList<>();
                queue.add(rootNode);
                while (!queue.isEmpty()) {
                    TagTreeNode node = queue.poll();
                    Tag tag = (Tag) node.getUserObject();
                    List<TagTreeNode> children = childNodes.get(tag.getId());
                    if (children != null && !children.isEmpty()) {
                        // Set children
                        for (TagTreeNode child : children) {
                            node.add(child);
                            queue.add(child);
                        }
                    }
                }
            }
            return rootNodes;
        }
        private static Map<String, List<TagTreeNode>> getChildNodes(List<Tag> tags) {
            Map<String, List<TagTreeNode>> map = new HashMap<>();
            for (Tag tag : tags) {
                if (tag.getPid() != null) {
                    if (!map.containsKey(tag.getPid())) {
                        List<TagTreeNode> nodes = new ArrayList<>();
                        map.put(tag.getPid(), nodes);
                    }
                    List<TagTreeNode> nodes = map.get(tag.getPid());
                    TagTreeNode node = createNode(tag);
                    nodes.add(node);
                }
            }
            return map;
        }
        private static TagTreeNode createNode(Tag tag) {
            return new TagTreeNode(tag);
        }
    }

}
