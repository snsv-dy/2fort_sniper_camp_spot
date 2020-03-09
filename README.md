# 2fort_sniper_camp_spot
A simple game inspired by playing as a sniper on 2fort in team fortress 2 and trying to headshoot snipers on the opposite side. I made this becouse I had some free time, and wanted to learn a little bit of java language.

You can compile the server using this command

`javac -cp ".:json-simple-1.1.jar" WSServer.java`

and run it using this

`java -cp ".:json-simple-1.1.jar" WSServer`

And you will probably have to, open the client using some sort of web server, becouse browser (at least firefox) won't import javascript modules. I used http server from python 3.

`python3 -m http.server`
