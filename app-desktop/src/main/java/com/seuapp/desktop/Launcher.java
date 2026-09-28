package com.seuapp.desktop;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class Launcher extends Application {

    private double dragOffsetX;
    private double dragOffsetY;

    @Override
    public void start(Stage primaryStage) throws Exception {

        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/main-view.fxml")
        );

        Parent root = loader.load();

        Scene scene = new Scene(root);

        // Remove a barra nativa do Windows
        primaryStage.initStyle(StageStyle.UNDECORATED);

        // Permite maximizar/minimizar
        primaryStage.setResizable(true);

        // Fundo transparente da Scene
        scene.setFill(Color.TRANSPARENT);

        // CSS
        scene.getStylesheets().add(
                getClass().getResource("/style.css").toExternalForm()
        );

        // =====================================================
        // ÍCONE DA APLICAÇÃO
        // =====================================================

        Image appIcon = new Image(
                getClass().getResourceAsStream("/icons/y96.jfif")
        );

        primaryStage.getIcons().add(appIcon);

        primaryStage.setTitle("Youtube Converter");

        // =====================================================
        // ELEMENTOS DA TITLE BAR
        // =====================================================

        HBox titleBar = (HBox) loader.getNamespace().get("titleBar");

        Button minimizeButton =
                (Button) loader.getNamespace().get("minimizeButton");

        Button maximizeButton =
                (Button) loader.getNamespace().get("maximizeButton");

        Button closeButton =
                (Button) loader.getNamespace().get("closeButton");

        ImageView appIconView =
                (ImageView) loader.getNamespace().get("appIconView");

        // Coloca o mesmo ícone dentro da title bar
        appIconView.setImage(appIcon);

        // =====================================================
        // ARRASTAR JANELA
        // =====================================================

        titleBar.setOnMousePressed(event -> {

            dragOffsetX = event.getSceneX();
            dragOffsetY = event.getSceneY();

        });

        titleBar.setOnMouseDragged(event -> {

            if (!primaryStage.isMaximized()) {

                primaryStage.setX(
                        event.getScreenX() - dragOffsetX
                );

                primaryStage.setY(
                        event.getScreenY() - dragOffsetY
                );
            }

        });

        // Duplo clique na barra = maximizar/restaurar
        titleBar.setOnMouseClicked(event -> {

            if (event.getClickCount() == 2) {

                primaryStage.setMaximized(
                        !primaryStage.isMaximized()
                );

            }

        });

        // =====================================================
        // MINIMIZAR
        // =====================================================

        minimizeButton.setOnAction(event -> {

            primaryStage.setIconified(true);

        });

        // =====================================================
        // MAXIMIZAR / RESTAURAR
        // =====================================================

        maximizeButton.setOnAction(event -> {

            primaryStage.setMaximized(
                    !primaryStage.isMaximized()
            );

        });

        // =====================================================
        // FECHAR
        // =====================================================

        closeButton.setOnAction(event -> {

            primaryStage.close();

        });

        // =====================================================
        // CENA
        // =====================================================

        primaryStage.setScene(scene);

        primaryStage.setMinWidth(520);
        primaryStage.setMinHeight(390);

        primaryStage.show();

        primaryStage.centerOnScreen();
    }

    public static void main(String[] args) {
        launch(args);
    }
}