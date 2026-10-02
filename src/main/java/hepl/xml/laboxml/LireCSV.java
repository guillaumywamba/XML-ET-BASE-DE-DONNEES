package hepl.xml.laboxml;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class LireCSV {
    public static void main(String[] args) {
        String fichier = "C:/Users/user/Desktop/HEPL BLOC3/XML/LABO/PADCHEST_chest_x_ray_images_labels_160K_01.02.19.csv";
        String ligne;

        try (BufferedReader br = new BufferedReader(new FileReader(fichier))) {
            while ((ligne = br.readLine()) != null) {
                // Sépare la ligne en utilisant la virgule comme séparateur
                String[] valeurs = ligne.split(",");

                // Affiche les éléments de la ligne
                for (String val : valeurs) {
                    System.out.print(val + " | ");
                }
                System.out.println();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
