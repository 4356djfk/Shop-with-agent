-- Quick audit report for category mismatch hotspots (ASCII-safe).
-- Run after category_quality_all_in_one.sql.

WITH product_text AS (
    SELECT
        id,
        name,
        category_path,
        lower(
            coalesce(name, '') || ' ' ||
            coalesce(brand, '') || ' ' ||
            coalesce(description, '')
        ) AS txt
    FROM products
),
flagged AS (
    SELECT
        id,
        name,
        category_path,
        CASE
            WHEN txt ~* '(headphone|earbud|earbuds|headset|bluetooth *ear|tws|wireless *ear)'
                 AND category_path !~* '(headphones|earphones|audio)'
                THEN 'HEADPHONE_MISMATCH'
            WHEN txt ~* '(ccd|camera|digital *camera|camcorder)'
                 AND txt !~* '(game *cd|music *cd|compact *disc|dvd|blu *-?ray|vinyl|record)'
                 AND category_path !~* '(camera)'
                THEN 'CAMERA_MISMATCH'
            WHEN txt ~* '(belt|leather *belt|waist *belt)'
                 AND category_path !~* '(belt)'
                THEN 'BELT_MISMATCH'
            WHEN txt ~* '(ice *maker|espresso|coffee *maker|coffee *machine)'
                 AND category_path !~* '(kitchen|coffee|ice)'
                THEN 'KITCHEN_APPLIANCE_MISMATCH'
            ELSE NULL
        END AS issue
    FROM product_text
)
SELECT
    issue,
    count(*) AS cnt
FROM flagged
WHERE issue IS NOT NULL
GROUP BY issue
ORDER BY cnt DESC, issue;
