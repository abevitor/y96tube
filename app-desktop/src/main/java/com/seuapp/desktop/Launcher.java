package com.seuapp.desktop;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.scene.shape.SVGPath;

import java.io.InputStream;

public class Launcher extends Application {

    private double dragOffsetX;
    private double dragOffsetY;

    private SVGPath criarIcone(String path) {
    SVGPath icon = new SVGPath();
    icon.setContent(path);
    icon.getStyleClass().add("svg-icon");
    return icon;
}

    @Override
    public void start(Stage primaryStage) throws Exception {

        // =====================================================
        // FXML
        // =====================================================

        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/main-view.fxml")
        );

        Parent root = loader.load();

        // =====================================================
        // CENA
        // =====================================================

        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);

        // Remove a barra padrão do Windows
        primaryStage.initStyle(StageStyle.UNDECORATED);

        primaryStage.setResizable(true);

        primaryStage.setMinWidth(520);
        primaryStage.setMinHeight(390);

        // =====================================================
        // CSS
        // =====================================================

        var cssUrl = getClass().getResource("/style.css");

        if (cssUrl == null) {
            throw new IllegalStateException(
                    "Não foi possível encontrar /style.css"
            );
        }

        scene.getStylesheets().add(
                cssUrl.toExternalForm()
        );

        // =====================================================
        // ÍCONE
        // =====================================================

        InputStream iconStream =
                getClass().getResourceAsStream("/icons/y96.jfif");

        Image appIcon = null;

        if (iconStream != null) {

            appIcon = new Image(iconStream);

            primaryStage.getIcons().add(appIcon);

        } else {

            System.out.println(
                    "AVISO: /icons/y96tube.png não encontrado."
            );

        }

        primaryStage.setTitle("Y96TUBE");

        // =====================================================
        // ELEMENTOS DA TITLE BAR
        // =====================================================

        HBox titleBar =
                (HBox) loader.getNamespace().get("titleBar");

        Button minimizeButton =
                (Button) loader.getNamespace().get("minimizeButton");

        Button maximizeButton =
                (Button) loader.getNamespace().get("maximizeButton");

        Button closeButton =
                (Button) loader.getNamespace().get("closeButton");

        Button accessibilityButton =
                (Button) loader.getNamespace().get("accessibilityButton");

        ImageView appIconView =
                (ImageView) loader.getNamespace().get("appIconView");

        // =====================================================
        // VALIDACAO
        // =====================================================

        if (titleBar == null) {
            throw new IllegalStateException(
                    "fx:id=\"titleBar\" não encontrado."
            );
        }

        if (minimizeButton == null) {
            throw new IllegalStateException(
                    "fx:id=\"minimizeButton\" não encontrado."
            );
        }

        if (maximizeButton == null) {
            throw new IllegalStateException(
                    "fx:id=\"maximizeButton\" não encontrado."
            );
        }

        if (closeButton == null) {
            throw new IllegalStateException(
                    "fx:id=\"closeButton\" não encontrado."
            );
        }

        if (accessibilityButton == null) {
            throw new IllegalStateException(
                    "fx:id=\"accessibilityButton\" não encontrado."
            );
        }

        if (appIconView == null) {
            throw new IllegalStateException(
                    "fx:id=\"appIconView\" não encontrado."
            );
        }

        // =====================================================
        // ICONE NA TITLE BAR
        // =====================================================

        if (appIcon != null) {
            appIconView.setImage(appIcon);
        }

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

        // =====================================================
        // DUPLO CLIQUE = MAXIMIZAR
        // =====================================================

        titleBar.setOnMouseClicked(event -> {

            if (event.getClickCount() == 2) {

                primaryStage.setMaximized(
                        !primaryStage.isMaximized()
                );

            }

        });

        // =====================================================
// ICONE MINIMIZAR
// =====================================================

SVGPath minimizeIcon = criarIcone(
        "M2 8 H14"
);

minimizeButton.setText("");
minimizeButton.setGraphic(minimizeIcon);

// =====================================================
// ICONE MAXIMIZAR
// =====================================================

SVGPath maximizeIcon = criarIcone(
        "M2 2 H14 V14 H2 Z"
);

maximizeButton.setText("");
maximizeButton.setGraphic(maximizeIcon);

// =====================================================
// ICONE FECHAR
// =====================================================

SVGPath closeIcon = criarIcone(
        "M2 2 L14 14 M14 2 L2 14"
);

closeButton.setText("");
closeButton.setGraphic(closeIcon);

// =====================================================
// ICONE ACESSIBILIDADE
// =====================================================

SVGPath darkModeIcon = criarIcone(
        "M14 8 A6 6 0 1 1 8 2 A5 5 0 0 0 14 8 Z"
);

accessibilityButton.setText("");
accessibilityButton.setGraphic(darkModeIcon);

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
// ALTERAR ICONE DE MAXIMIZAR
// =====================================================

primaryStage.maximizedProperty().addListener(
        (observable, oldValue, maximized) -> {

            if (maximized) {

                maximizeIcon.setContent(
                        "M3 5 H11 V13 H3 Z M5 3 H13 V11"
                );

            } else {

                maximizeIcon.setContent(
                        "M2 2 H14 V14 H2 Z"
                );
            }
        }
);

// =====================================================
// FECHAR
// =====================================================

closeButton.setOnAction(event -> {

    primaryStage.close();

});

// =====================================================
// ACESSIBILIDADE / MODO ESCURO
// =====================================================

Tooltip accessibilityTooltip =
        new Tooltip("Ativar modo escuro");

accessibilityButton.setTooltip(
        accessibilityTooltip
);

accessibilityButton.setOnAction(event -> {

    boolean darkMode =
            root.getStyleClass().contains("dark-mode");

    if (darkMode) {

        root.getStyleClass().remove("dark-mode");

        accessibilityTooltip.setText(
                "Ativar modo escuro"
        );

    } else {

        root.getStyleClass().add("dark-mode");

        accessibilityTooltip.setText(
                "Ativar modo claro"
        );
    }

});

        // =====================================================
        // MOSTRAR
        // =====================================================

        primaryStage.setScene(scene);

        primaryStage.show();

        primaryStage.centerOnScreen();
    }

    public static void main(String[] args) {
        launch(args);
    }
}