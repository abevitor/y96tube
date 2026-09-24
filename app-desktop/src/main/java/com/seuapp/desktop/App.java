package com.seuapp.desktop;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class App extends Application {

    @Override
public void start(Stage stage) {

    try {

        System.out.println("1 - iniciando App");

        FXMLLoader loader = new FXMLLoader(
                App.class.getResource("/main-view.fxml")
        );

        System.out.println(
                "2 - FXML encontrado: "
                        + loader.getLocation()
        );

        Parent root = loader.load();

        System.out.println("3 - FXML carregado");

        Scene scene = new Scene(root);

        stage.setTitle("Youtube Converter");
        stage.setScene(scene);

        System.out.println("4 - mostrando janela");

        stage.show();

        System.out.println("5 - janela mostrada");

    } catch (Exception e) {

        e.printStackTrace();
    }
}

    public static void main(String[] args) {
        launch(args);
    }
}