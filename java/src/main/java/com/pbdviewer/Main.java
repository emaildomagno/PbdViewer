package com.pbdviewer;

import com.pbdviewer.model.*;
import com.pbdviewer.utils.pbclass.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.Callback;

import java.io.File;

public class Main extends Application {

    private final ObservableList<TreeNode> nodes = FXCollections.observableArrayList();
    private TreeNode selectedNode;
    private TextArea textArea;
    private javafx.scene.control.TreeView<TreeNode> treeView;

    @Override
    public void start(Stage primaryStage) {
        // Left: TreeView
        treeView = new javafx.scene.control.TreeView<>();
        treeView.setShowRoot(false);
        treeView.setCellFactory(new Callback<>() {
            @Override
            public TreeCell<TreeNode> call(javafx.scene.control.TreeView<TreeNode> param) {
                return new TreeCell<>() {
                    @Override
                    protected void updateItem(TreeNode item, boolean empty) {
                        super.updateItem(item, empty);
                        if (empty || item == null) {
                            setText(null);
                        } else {
                            setText(item.getName());
                        }
                    }
                };
            }
        });

        // Root node (hidden)
        javafx.scene.control.TreeItem<TreeNode> rootItem = new javafx.scene.control.TreeItem<>();
        treeView.setRoot(rootItem);

        // Observe nodes list changes and update tree
        nodes.addListener((javafx.collections.ListChangeListener<TreeNode>) c -> {
            while (c.next()) {
                if (c.wasAdded()) {
                    for (TreeNode node : c.getAddedSubList()) {
                        rootItem.getChildren().add(toTreeItem(node));
                    }
                }
                if (c.wasRemoved()) {
                    for (TreeNode node : c.getRemoved()) {
                        rootItem.getChildren().removeIf(item -> item.getValue() == node);
                    }
                }
            }
        });

        // Selection listener
        treeView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                selectedNode = newVal.getValue();
                if (selectedNode != null && selectedNode.getText() != null) {
                    textArea.setText(selectedNode.getText());
                } else {
                    textArea.setText("");
                }
            }
        });

        // Delete key to remove file nodes
        treeView.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.DELETE) {
                javafx.scene.control.TreeItem<TreeNode> selected = treeView.getSelectionModel().getSelectedItem();
                if (selected != null && selected.getValue() != null &&
                        selected.getValue().getNodeType() == NodeType.File) {
                    nodes.remove(selected.getValue());
                }
            }
        });

        // Right: TextArea
        textArea = new TextArea();
        textArea.setEditable(false);
        textArea.setWrapText(false);
        textArea.setStyle("-fx-font-family: 'Monospaced'; -fx-font-size: 12;");

        // Layout
        SplitPane splitPane = new SplitPane();
        splitPane.getItems().addAll(treeView, textArea);
        splitPane.setDividerPositions(0.3);

        StackPane root = new StackPane(splitPane);

        Scene scene = new Scene(root, 1200, 800);

        // Drag-and-drop support
        scene.setOnDragOver(e -> {
            if (e.getDragboard().hasFiles()) {
                for (File file : e.getDragboard().getFiles()) {
                    String ext = getExtension(file.getName()).toLowerCase();
                    if (ext.equals(".exe") || ext.equals(".dll") ||
                            ext.equals(".pbd") || ext.equals(".pbl")) {
                        e.acceptTransferModes(TransferMode.COPY_OR_MOVE);
                        break;
                    }
                }
            }
            e.consume();
        });

        scene.setOnDragDropped(e -> {
            Dragboard db = e.getDragboard();
            boolean success = false;
            if (db.hasFiles()) {
                for (File file : db.getFiles()) {
                    String ext = getExtension(file.getName()).toLowerCase();
                    if (ext.equals(".exe") || ext.equals(".dll") ||
                            ext.equals(".pbd") || ext.equals(".pbl")) {
                        addFile(file.getAbsolutePath());
                        success = true;
                    }
                }
            }
            e.setDropCompleted(success);
            e.consume();
        });

        primaryStage.setTitle("PBD Viewer");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private void addFile(String filename) {
        try {
            PbProject project = new PbProject(filename);
            for (PbFile pbFile : project.getFiles()) {
                // Remove existing node with same path
                nodes.removeIf(o -> pbFile.getFilePath() != null &&
                        pbFile.getFilePath().equalsIgnoreCase(o.getName()));
                nodes.add(new FileNode(pbFile));
            }
        } catch (Exception ex) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("Failed to load file");
            alert.setContentText(ex.getMessage());
            alert.showAndWait();
        }
    }

    private javafx.scene.control.TreeItem<TreeNode> toTreeItem(TreeNode node) {
        javafx.scene.control.TreeItem<TreeNode> item = new javafx.scene.control.TreeItem<>(node);
        item.setExpanded(node.isExpanded());
        for (TreeNode child : node.getChildren()) {
            item.getChildren().add(toTreeItem(child));
        }
        return item;
    }

    private static String getExtension(String filename) {
        int idx = filename.lastIndexOf('.');
        return idx >= 0 ? filename.substring(idx) : "";
    }

    public static void main(String[] args) {
        launch(args);
    }
}
