=next=
========
- Upload your pending changes before updating: this update clears the downloaded countries, along with any change not uploaded yet. Download the countries again afterwards.
- Download and show only the climbing data mapped as the OpenStreetMap wiki's Climbing page describes it: routes, crags, areas, graded boulders, and climbing gyms, walls and towers. Cliffs and peaks tagged with sport=climbing, and crags mapped as a way, are no longer shown, on the map or in the search.
- Show the routes and crags tagged climbing=* without sport=climbing, as the wiki asks apps to.
- Download every level of nested climbing areas, and the climbing gyms mapped as multipolygons.

=2026.10-4=
========
- Fix uploading to OpenStreetMap: log in with OAuth 2, as OpenStreetMap no longer accepts the old login. Upload with a few words on what changed, and for anything that also changed on OpenStreetMap since the download, choose between your version and theirs.
- Rework the editor: add, move, edit and delete routes, route tops, climbing gyms, walls and towers, with their tags written as the OpenStreetMap wiki describes them. The editors have a striped border, to tell them from the info views.
- Edit crags and areas: create one, add routes to a crag or crags to an area from the map, drag them into their order, take them out, or delete the crag or area. When adding a route to a crag, the crags in view are offered, and any crag can be searched for.
- Pick the grading system of a route's grade in the editor, as OpenStreetMap now keeps grades in their local system. It starts on the system the route is graded in, and the grade is saved in the system picked.
- Base the grade tables on Wikipedia's comparison tables, add the Polish (Kurtyka) and Fontainebleau systems, and remove the French British, WI and mixed ones. A removed system picked in the settings becomes UIAA.
- Show grades the same way everywhere, the grade pickers included, and use bigger entry titles in the info views and editors.
- Leave the grade range of crags and areas out of the editor: their grades are those of their routes.
- Translate the app to French.
- Bring back the OpenTopoMap map style.
- Show the line of the routes mapped as a way in the A.R. view, from their bottom to a pin at their top.
- Show a crag's routes in the A.R. view once it is close enough to make them out, and its pin from farther away.
- Shrink far pins less in the A.R. view, so distant crags stay visible, and smooth its movement.
- Keep the A.R. virtual terrain in place while moving.
- Warn on the map, A.R., editor and environment compasses when the compass needs calibrating, or when something nearby disturbs it.
- Follow the camera instead of the top of the screen once the phone is held up, without flipping back and forth in between.
- Tell when the device has no compass, which the map needs to turn with you and the A.R. view to place the routes around you.
- Use slim route pins on the map and in the info views too, and always outline crags and areas by the shape around their routes.
- Update the download reminder when a country download ends or the last country is deleted.
- Offer one APK per CPU type next to the universal one, and compress the native libraries, for a smaller download. Leave out the encrypted dependency list that F-Droid and IzzyOnDroid flag.

=2026.10-3=
========
- Download climbing data in the background, with a notification. Several countries can be queued, and each download, or all of them, can be cancelled.
- Show when each downloaded country was last updated and how many routes it has, and mark the downloads that failed.
- Add a Near you list to the country downloads, and show the country names in the device language.
- Add the missing countries to the download list.
- Ask before downloading climbing data over mobile data, which is now off by default.
- Stop retrying country downloads that would only fail again.
- Tap a map cluster to list the areas, crags and routes it holds, with a button to zoom into it.
- Show what a route, crag or area is part of in its info view, with its pin to open it. At the top level, show the country instead.
- Add a quick filter to the crag and area views, to find a route or crag by name, and a clear button to the filter and search fields.
- Number the routes on their map and A.R. pins, by their order in the crag.
- Count a route mapped with several elements, such as its bottom and top, only once.
- Prefer the UIAA grade when a route has grades in several systems, and recognise Hueco grades.
- Use slim route pins in the A.R. view, so that the routes along a cliff overlap less, with their own pin for route tops.
- Outline crags and areas on the ground in the A.R. view, as on the map.
- Add a settings button to the A.R. view.
- Split the A.R. horizon setting in two: the horizon style (none, horizon or virtual terrain), and whether routes follow the elevation. The horizon button cycles through every combination.
- Build the A.R. virtual terrain closer, and fix its flickering and gaps.
- Fix the map staying zoomed out after first finding the location on a fresh install.
- Edit the walkie-talkie channel and callsign straight from the walkie-talkie view.
- Reconnect to walkie-talkie buddies more reliably.
- Remove the unused Wi-Fi, Wi-Fi Direct and Bluetooth walkie-talkie settings, and the permissions they needed.

=2026.10-2=
========
- Add a My Log Book tool to browse, search and filter the personal log book, and to edit, locate or delete its entries.
- Add log book backup: save it to a file and restore it from one. The log book is also included in the Android device backup.
- Add an onsight climbed status to the log book.
- Tap a grade to see it converted to the other grade systems.
- Rework the crag route list: routes are shown as icons with their name, numbered by their order.
- Place A.R. routes at their elevation, using the Mapterhorn terrain model.
- Add an A.R. horizon toggle: off, horizon, horizon with elevation, or a wireframe of the surrounding terrain.
- Warn when the compass needs calibrating.
- Fix A.R. field of view and route position calculations.
- Reduce the default A.R. view distance to 2.5 km.
- Use WebRTC voice detection for the hands-free walkie-talkie, with a noise filter setting instead of the trigger sensitivity.
- Default the walkie-talkie to push to talk.
- Target Android 17.

=2026.10-1=
========
- Allow each different map view to have its own orientation style saved. 
- Port the map to MapLibre.
- Extend the info views to climbing areas and crags.
- Add Wi-Fi Aware support to the walkie-talkie.
- Add a personal log book: private notes on areas, crags and routes, and a climbed status for routes (flashed, sent, completed or not completed). Stored on the device only and kept when OSM data is updated or removed.

=2022.05-1=
========
- Add sunrise sunset calculator to the environment view.
- Add rotatable bazel to the compass view.
- Add temperature scales to the unit converter.
- lots of bugfixes

=2020.09=
========
- New icon rendering engine. Allows for a lot faster and more efficient rendering. This allows for individually customizable icons.
- Icons now display grade info, route name, and a pictographic representation of the climbing style.
- Text on icons now has an outline. Makes it easier to read in bad contrast conditions.
- Add Z-Indexing for map POIs. This will give a nice clean display while the map it being rotated.
- Implement frame limiting for map rendering. Should help a lot with preserving battery life.
- Add download button to the map view.
- Add support for manual map rotation.
- Fix bounding box not wrapping around ante-meridian.
- Implement routes searching.
- Add go-to location button on POI info dialogue box.

=2019.03=
========
- Add centre map on point functionality.
- Make upload data list elements clickable.
- Change country list lazy load.
- Add info dialog for cluster markers.
- change route icons so that now they can also display the route grade.
- Show climbing gyms.
- Show climbing crags even if they don't have any routes.
- Finish implementing walkie-talkie.
- New edit activity to allow for the new climbing tags.
- Open current edited node in external editors (web Id editor, Vespucci editor).
- Implement a JOSM tags editor.
