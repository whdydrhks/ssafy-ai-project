import 'package:flutter/material.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:get/get.dart';
import 'package:test_app/src/controller/calendar/calendar_controller.dart';

class CalendarPage extends StatelessWidget {
  final controller = Get.put(CalendarController(), permanent: true);
  final storage = const FlutterSecureStorage();

  CalendarPage({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
        appBar: AppBar(),
        body: Column(
          children: [
            Text("${storage.read(key: 'userId')}"),
            FloatingActionButton(
              onPressed: () {
                controller.navigateToDateWrite();
              },
            )
          ],
        ));
  }
}
