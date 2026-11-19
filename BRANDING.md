# Token Branding

A token's icon and background color are chosen by `TokenIcon`, in this order:

1. The `image` and `color` parameters of the token URI (see [URI.md](URI.md)).
2. A resource matching the issuer: the issuer (from the `issuer` parameter or label prefix) is
   lowercased, spaces are removed and other non-alphanumeric runs become `_`. The result, with a
   `fa_` prefix, is looked up as a drawable and, with a `brand_` prefix, as a color. For example,
   `GitLab.com` → `fa_gitlab_com` / `brand_gitlab_com`.
3. Fallbacks: a generic icon and a color from `R.array.backgrounds`, picked from the issuer hash.

## Adding an issuer

- **Icon**: Font Awesome icons live as vector drawables named `fa_<icon>.xml` in `res/drawable`.
  If the issuer name doesn't match an icon name, add an alias to
  [`res/values/drawables.xml`](mobile/src/main/res/values/drawables.xml):

  ```xml
  <drawable name="fa_gitlab_com" tools:keep="@drawable/fa_gitlab_com">@drawable/fa_gitlab</drawable>
  ```

- **Color**: add a dark color from the brand's palette to
  [`res/values/colors.xml`](mobile/src/main/res/values/colors.xml):

  ```xml
  <color name="brand_bitbucket" tools:keep="@color/brand_bitbucket">#0747A6</color>
  ```

Keep the `tools:keep` attribute: these resources are only looked up by name at runtime, so resource
shrinking would otherwise remove them.
