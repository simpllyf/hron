import * as hron from "hron-wasm";

export default {
  fetch() {
    return Response.json({
      exports: Object.keys(hron),
      cron: hron.Schedule.parse("every weekday at 9:00").toCron(),
    });
  },
};
